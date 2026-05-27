package com.sparta.logistics.order.order.saga;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.CancelDeliveryCommand;
import com.sparta.logistics.common.kafka.event.RestoreStockCommand;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// TODO: Orchestration Saga 보상 트랜잭션 구현

/**
 * Orchestration Saga Orchestrator
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelOrderOrchestrator {

    private final OrderRepository orderRepository;
    private final KafkaTemplate kafkaTemplate;

    /**
     * Saga Step 3-1: CANCELLING 전이 + cancel.delivery.command 발행
     * <p>
     * OrderService.cancelOrder()에서 권한 검사 및 주문 조회 완료 후 호출됨
     * 파티션 키 = orderId → 동일 주문의 모든 명령이 동일 파티션에서 순서 보장됨
     * */
    @Transactional
    public void start(Order order, UUID userId, String cancelReason) {
        order.startCancelling(userId, cancelReason);
        orderRepository.save(order);

        kafkaTemplate.send(
                KafkaTopics.CANCEL_DELIVERY_COMMAND,
                order.getId().toString(),
                CancelDeliveryCommand.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(order.getId())
                        .deliveryId(order.getDeliveryId())
                        .build()
        );
        log.info("[CancelSaga] CANCELLING 전이 + cancel.delivery.command 발행 orderId={}", order.getId());
    }

    /**
     * Saga Step 3-3: delivery.cancelled.ack 수신 → restore.stock.command 발행
     * <p>
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     * */
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
        log.info("[CancelSaga] restore.stock.command 발행 orderId={} itemCount={}", orderId, items.size());
    }

    /**
     * Saga Step 3-5: stock.restored.ack 수신 → CANCELLED 확정
     * <p>
     * cancelledBy, cancelReason은 start() 시점에 이미 저장되어 있음
     * CANCELLING 상태가 아니면 예외 없이 무시 (Kafka 불필요 재시도 방지)
     * */
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
        log.info("[CancelSaga] CANCELLED 확정 orderId={}", orderId);
    }
}
