package com.sparta.logistics.order.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderItem;
import com.sparta.logistics.order.domain.OrderStatus;
import com.sparta.logistics.order.dto.OrderItemData;
import com.sparta.logistics.order.dto.response.OrderDetailResponse;
import com.sparta.logistics.order.dto.response.OrderSummaryResponse;
import com.sparta.logistics.order.exception.OrderErrorCode;
import com.sparta.logistics.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 주문 생성
     * 1. Order 및 OrderItem 생성 (PENDING) ✅
     * 2. Hub Service에 재고 예약 요청
     * 3. Delivery Service에서 배송 및 경로 자동 생성
     * 4. Notification Service에서 AI 발송 시간 계산 후 슬랙 알림 발송
     * */
    @Transactional
    public OrderDetailResponse createOrder(
            UUID requesterCompanyId,
            UUID receiverCompanyId,
            LocalDateTime dueDate,
            String requestMemo,
            List<OrderItemData> items,
            UUID userId
    ) {
        Order order = Order.create(requesterCompanyId, receiverCompanyId, userId, dueDate, requestMemo);

        items.forEach(item -> {
            OrderItem orderItem = OrderItem.create(
                    order,
                    item.productId(),
                    item.productName(),
                    item.unitPrice(),
                    item.quantity()
            );
            order.addOrderItem(orderItem);
        });

        order.calculateTotalAmount();
        orderRepository.save(order);
        return OrderDetailResponse.from(order);
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
        return OrderDetailResponse.from(order);
    }

    /** 주문 수정 **/
    @Transactional
    public OrderDetailResponse updateOrder(UUID orderId, LocalDateTime dueDate, String requestMemo, UUID userId, Role role) {
        // 수정은 HUB_MANAGER 또는 MASTER만 가능
        if (!isAdminRole(role)) {
            throw new BusinessException(OrderErrorCode.ORDER_UPDATE_PERMISSION_DENIED);
        }

        // TODO: HUB_MANAGER는 본인 담당 허브만 수정 가능해야 함

        Order order = findOrder(orderId);

        // CANCELLED 또는 COMPLETED 상태는 수정 불가
        if (!order.isModifiable()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_MODIFIABLE);
        }

        order.update(dueDate, requestMemo);
        return OrderDetailResponse.from(order);
    }

    /**
     * 주문 취소
     * 1. Delivery Service: 배송 및 경로 취소
     * 2. Hub Service: 재고 복구
     * 3. 주문 상태 -> CANCELLED ✅
     * */
    @Transactional
    public OrderDetailResponse cancelOrder(UUID orderId, String cancelReason, UUID userId, Role role) {
        // 취소는 HUB_MANAGER 또는 MASTER만 가능
        if (!isAdminRole(role)) {
            throw new BusinessException(OrderErrorCode.ORDER_CANCEL_PERMISSION_DENIED);
        }

        // TODO: HUB_MANAGER는 본인 담당 허브만 취소 가능해야 함

        Order order = findOrder(orderId);

        // CANCELLED 또는 COMPLETED 상태는 취소 불가
        if (!order.isModifiable()) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }

        order.cancel(userId, cancelReason);
        return OrderDetailResponse.from(order);
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
}
