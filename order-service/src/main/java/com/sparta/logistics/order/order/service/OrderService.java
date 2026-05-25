package com.sparta.logistics.order.order.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.CancelDeliveryCommand;
import com.sparta.logistics.common.kafka.event.OrderCreatedEvent;
import com.sparta.logistics.common.kafka.event.OrderItemPayload;
import com.sparta.logistics.common.kafka.event.RestoreStockCommand;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.order.client.CompanyServiceClient;
import com.sparta.logistics.order.client.ProductServiceClient;
import com.sparta.logistics.order.client.response.CompanyResponse;
import com.sparta.logistics.order.client.response.ProductResponse;
import com.sparta.logistics.order.exception.OrderErrorCode;
import com.sparta.logistics.order.order.dto.response.OrderDetailResponse;
import com.sparta.logistics.order.order.dto.response.OrderSummaryResponse;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.repository.OrderRepository;
import com.sparta.logistics.order.orderitem.dto.request.OrderItemRequest;
import com.sparta.logistics.order.orderitem.entity.OrderItem;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CompanyServiceClient companyServiceClient;
    private final ProductServiceClient productServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 주문 생성
     * 1. requesterCompanyId / receiverCompanyId 존재 여부 검증 (Company Service) ✅
     * 2. 각 상품 정보(이름·단가) 조회 (Product Service) ✅
     * 3. Order 및 OrderItem 생성 (PENDING) ✅
     * 4. Hub Service에 재고 예약 요청 // TODO: Kafka Choreography Saga
     * 5. Delivery Service에서 배송 및 경로 자동 생성 // TODO: Kafka Choreography Saga
     * 6. Notification Service에서 AI 발송 시간 계산 후 슬랙 알림 발송 // TODO: Kafka Choreography Saga
     * */
    @Transactional
    public OrderDetailResponse createOrder(
            UUID requesterCompanyId,
            UUID receiverCompanyId,
            LocalDateTime dueDate,
            String requestMemo,
            List<OrderItemRequest> items,
            UUID userId
    ) {
        // 업체가 존재하는지 검증
        validateCompanyExists(requesterCompanyId);
        validateCompanyExists(receiverCompanyId);

        Order order = Order.create(requesterCompanyId, receiverCompanyId, userId, dueDate, requestMemo);

        // 동일 productId의 quantity 합산 (중복 OrderItem row 생성 방지)
        Map<UUID, Integer> mergedItems = items.stream()
                .collect(Collectors.groupingBy(
                        OrderItemRequest::getProductId,
                        Collectors.summingInt(OrderItemRequest::getQuantity)
                ));

        mergedItems.forEach((productId, quantity) -> {
            ProductResponse product = fetchProduct(productId);
            OrderItem orderItem = OrderItem.create(
                    order,
                    productId,
                    product.name(),
                    product.price(),
                    quantity,
                    product.hubId()
            );
            order.addOrderItem(orderItem);
        });

        order.calculateTotalAmount();
        orderRepository.save(order);

        // Choreography Saga Step 1-1: order.created 이벤트 발행 → HubService 재고 예약 트리거
        publishOrderCreatedEvent(order);

        return OrderDetailResponse.from(order);
    }

    /**
     * Choreography Saga Step 1-4: delivery.created 이벤트 수신 후 주문 상태를 ACCEPTED로 전이하고 deliveryId를 저장함
     * DeliveryCreatedConsumer에서 호출됨
     * 멱등성 보장: PENDING 상태가 아닌 경우 이미 처리된 이벤트로 간주하고 무시함
     * */
    @Transactional
    public void acceptOrder(UUID orderId, UUID deliveryId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("[delivery.created] 주문을 찾을 수 없음 — 무시 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("[delivery.created] 멱등성 처리: 이미 처리된 주문 — 무시 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        order.accept();
        order.linkDelivery(deliveryId);
        log.info("[delivery.created] 주문 ACCEPTED 전이 완료 orderId={} deliveryId={}", orderId, deliveryId);
    }

    /**
     * Choreography Saga 보상 트랜잭션: 재고 예약 실패 또는 배송 생성 실패 시 주문을 즉시 CANCELLED 처리함
     * StockReservationFailedConsumer / DeliveryCreationFailedConsumer에서 호출됨
     * <p>
     * 멱등성 보장: 이미 CANCELLED인 경우 재처리 시 no-op
     */
    @Transactional
    public void cancelOrderByCompensation(UUID orderId, String reason) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("[보상 취소] 주문을 찾을 수 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("[보상 취소] 멱등성 처리: 이미 취소된 주문 orderId={}", orderId);
            return;
        }

        order.cancel(null, reason);
        log.info("[보상 취소] 주문 CANCELLED orderId={} reason={}", orderId, reason);
    }

    /** 주문 목록 조회 **/
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getOrders(
            UUID requesterCompanyId,
            UUID receiverCompanyId,
            OrderStatus status,
            LocalDateTime dueDateFrom,
            LocalDateTime dueDateTo,
            Role role,
            UUID userId,
            Pageable pageable
    ) {
        // MASTER, HUB_MANAGER는 전체 조회, 그 외는 본인 주문만 가능
        return orderRepository.search(
                isAdminRole(role) ? null : userId,
                requesterCompanyId,
                receiverCompanyId,
                status,
                dueDateFrom,
                dueDateTo,
                pageable
        ).map(OrderSummaryResponse::from);
    }

    /** 주문 단건 조회 **/
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(UUID orderId, UUID userId, Role role) {
        Order order = findOrder(orderId);
        checkPermission(order, userId, role);

        String requesterCompanyName = fetchCompanyName(order.getRequesterCompanyId());
        String receiverCompanyName = fetchCompanyName(order.getReceiverCompanyId());

        return OrderDetailResponse.from(order, requesterCompanyName, receiverCompanyName);
    }

    /** 주문 수정 **/
    @Transactional
    public OrderDetailResponse updateOrder(UUID orderId, LocalDateTime dueDate, String requestMemo, UUID userId, Role role, UUID userHubId) {
        // 수정은 HUB_MANAGER 또는 MASTER만 가능
        if (!isAdminRole(role)) {
            throw new BusinessException(OrderErrorCode.ORDER_UPDATE_PERMISSION_DENIED);
        }

        Order order = findOrder(orderId);

        // HUB_MANAGER는 본인 담당 허브 소속 업체의 주문만 수정 가능
        if (role == Role.HUB_MANAGER) {
            checkHubPermission(order.getRequesterCompanyId(), userHubId);
        }

        // CANCELLED COMPLETED, IN_DELIVERY 상태는 수정 불가
        if (!order.isModifiable()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_MODIFIABLE);
        }

        order.update(dueDate, requestMemo);
        return OrderDetailResponse.from(order);
    }

    /**
     * 주문 취소 요청 (Orchestration Saga Step 3-1)
     * <p>
     * 즉시 CANCELLED 처리하지 않고 CANCELLING 상태로 전이한 뒤 cancel.delivery.command를 발행함
     * 실제 CANCELLED 확정은 stock.restored.ack 수신 후 confirmOrderCancelled()가 담당함
     * <p>
     * 상태 전이 유효성은 Order.startCancelling()이 내부에서 처리함 (canTransitionTo 테이블)
     * */
    @Transactional
    public OrderDetailResponse cancelOrder(UUID orderId, String cancelReason, UUID userId, Role role, UUID userHubId) {
        // 취소는 HUB_MANAGER 또는 MASTER만 가능
        if (!isAdminRole(role)) {
            throw new BusinessException(OrderErrorCode.ORDER_CANCEL_PERMISSION_DENIED);
        }

        Order order = findOrder(orderId);

        // HUB_MANAGER는 본인 담당 허브 소속 업체의 주문만 취소 가능
        if (role == Role.HUB_MANAGER) {
            checkHubPermission(order.getRequesterCompanyId(), userHubId);
        }

        // CANCELLING 전이 + 취소자 및 취소 사유 저장 (내부에서 상태 전이 유효성 검사)
        order.startCancelling(userId, cancelReason);
        orderRepository.save(order);

        // Orchestration Saga Step 3-1: cancel.delivery.command 발행 → DeliveryService 배송 취소 트리거
        kafkaTemplate.send(
                KafkaTopics.CANCEL_DELIVERY_COMMAND,
                order.getId().toString(),
                CancelDeliveryCommand.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(order.getId())
                        .deliveryId(order.getDeliveryId()) // PENDING 상태면 null일 수 있음
                        .build()
        );
        log.info("[cancelOrder] CANCELLING 전이 + cancel.delivery.command 발행 orderId={}", order.getId());

        return OrderDetailResponse.from(order);
    }

    /**
     * Orchestration Saga Step 3-3: delivery.cancelled.ack 수신 후 restore.stock.command 발행
     * DeliveryCancelledAckConsumer에서 호출됨
     * <p>
     * CANCELLING 상태가 아니면 이미 처리된 이벤트로 간주하고 무시함
     * */
    @Transactional
    public void handleDeliveryCancelled(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("[delivery.cancelled.ack] 주문을 찾을 수 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[delivery.cancelled.ack] 멱등성 처리: CANCELLING 상태가 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        List<RestoreStockItemPayload> items = order.getOrderItems().stream()
                .map(item -> RestoreStockItemPayload.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        kafkaTemplate.send(
                KafkaTopics.RESTORE_STOCK_COMMAND,
                orderId.toString(),
                RestoreStockCommand.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(orderId)
                        .orderItems(items)
                        .build()
        );
        log.info("[delivery.cancelled.ack] restore.stock.command 발행 orderId={} itemCount={}",
                orderId, items.size());
    }

    /**
     * Orchestration Saga Step 3-5: stock.restored.ack 수신 후 주문 CANCELLED 확정
     * StockRestoredAckConsumer에서 호출됨
     * <p>
     * CANCELLING 상태가 아니면 이미 처리된 이벤트로 간주하고 무시함
     * */
    @Transactional
    public void confirmOrderCancelled(UUID orderId) {
        Order order = orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElse(null);

        if (order == null) {
            log.warn("[stock.restored.ack] 주문을 찾을 수 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[stock.restored.ack] 멱등성 처리: CANCELLING 상태가 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        order.confirmCancelled();
        log.info("[stock.restored.ack] 주문 CANCELLED 확정 orderId={}", orderId);
    }

    // ===== Kafka 이벤트 발행 =====

    /**
     * order.created 이벤트를 Kafka로 발행함
     * 파티션 키: orderId — 동일 주문의 이벤트 순서를 보장함
     * */
    private void publishOrderCreatedEvent(Order order) {
        List<OrderItemPayload> payloads = order.getOrderItems().stream()
                .map(item -> OrderItemPayload.builder()
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .hubId(item.getHubId())
                        .build())
                .toList();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID()) // 중복 소비 방지를 위한 이벤트 고유 ID
                .orderId(order.getId())
                .orderItems(payloads)
                .requesterCompanyId(order.getRequesterCompanyId())
                .receiverCompanyId(order.getReceiverCompanyId())
                .build();

        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, order.getId().toString(), event);
        log.info("[order.created] 이벤트 발행 orderId={} itemCount={}", order.getId(), payloads.size());
    }

    private void validateCompanyExists(UUID companyId) {
        try {
            companyServiceClient.checkCompanyExists(companyId);
        } catch (FeignException.NotFound e) {
            throw new BusinessException(OrderErrorCode.COMPANY_NOT_FOUND);
        } catch (FeignException e) {
            throw new BusinessException(OrderErrorCode.COMPANY_SERVICE_UNAVAILABLE);
        }
    }

    private ProductResponse fetchProduct(UUID productId) {
        try {
            // AVAILABLE 상태가 아닌 상품은 주문 불가
            ProductResponse product = productServiceClient.getProduct(productId).data();
            if (!"AVAILABLE".equals(product.status())) {
                throw new BusinessException(OrderErrorCode.PRODUCT_NOT_AVAILABLE);
            }
            return product;
        } catch (FeignException.NotFound e) {
            throw new BusinessException(OrderErrorCode.PRODUCT_NOT_FOUND);
        } catch (FeignException e) {
            throw new BusinessException(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE);
        }
    }

    private String fetchCompanyName(UUID companyId) {
        try {
            CompanyResponse company = companyServiceClient.getCompany(companyId).data();
            return company != null ? company.name() : null;
        } catch (FeignException e) {
            log.warn("[FeignClient] 업체 이름 조회 실패 companyId={} status={}", companyId, e.status());
            return null;
        }
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findByIdAndDeletedAtIsNull(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // MASTER, HUB_MANAGER 이외의 사용자가 본인 것이 아닌 주문에 접근하면 안 됨
    private void checkPermission(Order order, UUID userId, Role role) {
        if (!isAdminRole(role) && !order.getRequesterUserId().equals(userId)) {
            throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private boolean isAdminRole(Role role) {
        return Role.MASTER == role || Role.HUB_MANAGER == role;
    }

    private void checkHubPermission(UUID requesterCompanyId, UUID userHubId) {
        try {
            CompanyResponse company = companyServiceClient.getCompany(requesterCompanyId).data();
            if (company == null || !company.hubId().equals(userHubId)) {
                throw new BusinessException(OrderErrorCode.ORDER_HUB_ACCESS_DENIED);
            }
        } catch (FeignException e) {
            throw new BusinessException(OrderErrorCode.COMPANY_SERVICE_UNAVAILABLE);
        }
    }
}
