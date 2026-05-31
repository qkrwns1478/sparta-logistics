package com.sparta.logistics.order.order.saga;

import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.order.kafka.producer.OrderEventPublisher;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.lock.OrderLockManager;
import com.sparta.logistics.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestration Saga Orchestrator
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelOrderOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderLockManager orderLockManager;

    /**
     * Saga Step 3-1: CANCELLING 전이 + cancel.delivery.command 발행
     * <p>
     * OrderService.cancelOrder()에서 권한 검사 및 주문 조회 완료 후 호출됨
     * 파티션 키 = orderId → 동일 주문의 모든 명령이 동일 파티션에서 순서 보장됨
     **/
    @Transactional
    public void start(Order order, UUID userId, String cancelReason) {
        order.startCancelling(userId, cancelReason);
        orderRepository.save(order);

        orderEventPublisher.publishCancelDeliveryCommand(order.getId(), order.getDeliveryId());
        log.info("[CancelSaga] CANCELLING 전이 + cancel.delivery.command 발행 orderId={}", order.getId());
    }

    /**
     * Saga Step 3-3: delivery.cancelled.ack 수신 → restore.stock.command 발행
     * <p>
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     **/
    @Transactional
    public void onDeliveryCancelled(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("[CancelSaga] onDeliveryCancelled 주문 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[CancelSaga] onDeliveryCancelled CANCELLING 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        List<RestoreStockItemPayload> items = order.getOrderItems().stream()
                .map(item -> RestoreStockItemPayload.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .hubId(item.getHubId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        orderEventPublisher.publishRestoreStockCommand(orderId, items);
    }

    /**
     * Saga 보상 Step 4-1: delivery.cancellation.failed 수신 → CANCELLING 이전 상태로 복구
     * <p>
     * deliveryId 유무로 이전 상태를 추론함
     *   - deliveryId == null → PENDING (delivery.created 수신 전)
     *   - deliveryId != null → ACCEPTED (delivery.created 수신 후)
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     **/
    @Transactional
    public void onDeliveryCancellationFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("[CancelSaga] onDeliveryCancellationFailed 주문 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[CancelSaga] onDeliveryCancellationFailed CANCELLING 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        OrderStatus previous = order.getDeliveryId() == null ? OrderStatus.PENDING : OrderStatus.ACCEPTED;
        order.revertCancelling(previous);
        orderLockManager.clearRestoreRetry(orderId);
        log.info("[CancelSaga] CANCELLING 복구 orderId={} restoredStatus={}", orderId, previous);
    }

    /**
     * Saga 보상 Step 4-2: stock.restoration.failed 수신 → restore.stock.command 재발행
     * <p>
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     **/
    @Transactional
    public void onStockRestorationFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("[CancelSaga] onStockRestorationFailed 주문 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[CancelSaga] onStockRestorationFailed CANCELLING 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        List<RestoreStockItemPayload> items = order.getOrderItems().stream()
                .map(item -> RestoreStockItemPayload.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .hubId(item.getHubId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        int retryCount = orderLockManager.incrementAndGetRestoreRetry(orderId);
        if (retryCount > OrderLockManager.MAX_RESTORE_RETRY) {
            log.warn("[CancelSaga] restore.stock.command 최대 재시도 초과 orderId={} retryCount={}", orderId, retryCount);
            return;
        }

        orderEventPublisher.publishRestoreStockCommand(orderId, items);
        log.info("[CancelSaga] restore.stock.command 재발행 orderId={} retryCount={} itemCount={}",
                orderId, retryCount, items.size());
    }

    /**
     * Saga Step 3-5: stock.restored.ack 수신 → CANCELLED 확정
     * <p>
     * cancelledBy, cancelReason은 start() 시점에 이미 저장되어 있음
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     **/
    @Transactional
    public void onStockRestored(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);

        if (order == null) {
            log.warn("[CancelSaga] onStockRestored 주문 없음 orderId={}", orderId);
            return;
        }

        if (order.getStatus() != OrderStatus.CANCELLING) {
            log.warn("[CancelSaga] onStockRestored CANCELLING 아님 orderId={} status={}",
                    orderId, order.getStatus());
            return;
        }

        order.confirmCancelled();
        orderLockManager.clearRestoreRetry(orderId);
        log.info("[CancelSaga] CANCELLED 확정 orderId={}", orderId);
    }
}
