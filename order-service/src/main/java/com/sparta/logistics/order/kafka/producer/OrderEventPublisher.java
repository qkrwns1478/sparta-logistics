package com.sparta.logistics.order.kafka.producer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.CancelDeliveryCommand;
import com.sparta.logistics.common.kafka.event.OrderCreatedEvent;
import com.sparta.logistics.common.kafka.event.OrderItemPayload;
import com.sparta.logistics.common.kafka.event.RestoreStockCommand;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.common.outbox.OutboxEventPublisher;
import com.sparta.logistics.order.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Order 도메인 이벤트를 Outbox 테이블에 저장함
 * 실제 Kafka 발행은 OutboxEventRelay가 담당함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final OutboxEventPublisher outboxEventPublisher;

    public void publishOrderCreated(Order order, UUID sourceHubId, UUID destinationHubId) {
        List<OrderItemPayload> payloads = order.getOrderItems().stream()
                .map(item -> OrderItemPayload.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .hubId(item.getHubId())
                        .build())
                .toList();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(UUID.randomUUID()) // 컨슈머 측 중복 제거(dedup)에 사용됨
                .orderId(order.getId())
                .orderItems(payloads)
                .requesterCompanyId(order.getRequesterCompanyId())
                .receiverCompanyId(order.getReceiverCompanyId())
                .sourceHubId(sourceHubId)
                .destinationHubId(destinationHubId)
                .build();

        outboxEventPublisher.publish(KafkaTopics.ORDER_CREATED, order.getId().toString(), "ORDER", event);
        log.info("[Outbox] order.created 저장 orderId={} itemCount={}", order.getId(), payloads.size());
    }

    public void publishCancelDeliveryCommand(UUID orderId, UUID deliveryId) {
        CancelDeliveryCommand command = CancelDeliveryCommand.builder()
                .eventId(UUID.randomUUID()) // 컨슈머 측 중복 제거(dedup)에 사용됨
                .orderId(orderId)
                .deliveryId(deliveryId)
                .build();

        outboxEventPublisher.publish(KafkaTopics.CANCEL_DELIVERY_COMMAND, orderId.toString(), "ORDER", command);
        log.info("[Outbox] cancel.delivery.command 저장 orderId={}", orderId);
    }

    public void publishRestoreStockCommand(UUID orderId, List<RestoreStockItemPayload> items) {
        RestoreStockCommand command = RestoreStockCommand.builder()
                .eventId(UUID.randomUUID()) // 컨슈머 측 중복 제거(dedup)에 사용됨
                .orderId(orderId)
                .orderItems(items)
                .build();

        outboxEventPublisher.publish(KafkaTopics.RESTORE_STOCK_COMMAND, orderId.toString(), "ORDER", command);
        log.info("[Outbox] restore.stock.command 저장 orderId={} itemCount={}", orderId, items.size());
    }
}
