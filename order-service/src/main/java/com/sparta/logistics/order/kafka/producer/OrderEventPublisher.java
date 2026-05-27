package com.sparta.logistics.order.kafka.producer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.CancelDeliveryCommand;
import com.sparta.logistics.common.kafka.event.OrderCreatedEvent;
import com.sparta.logistics.common.kafka.event.OrderItemPayload;
import com.sparta.logistics.common.kafka.event.RestoreStockCommand;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.order.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Choreography Saga Step 1-1: order.created 발행
     * 파티션 키: orderId
     **/
    public void publishOrderCreated(Order order) {
        List<OrderItemPayload> payloads = order.getOrderItems().stream()
                .map(item -> OrderItemPayload.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .hubId(item.getHubId())
                        .build())
                .toList();

        kafkaTemplate.send(
                KafkaTopics.ORDER_CREATED,
                order.getId().toString(),
                OrderCreatedEvent.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(order.getId())
                        .orderItems(payloads)
                        .requesterCompanyId(order.getRequesterCompanyId())
                        .receiverCompanyId(order.getReceiverCompanyId())
                        .build()
        );
        log.info("[order.created] 발행 orderId={} itemCount={}", order.getId(), payloads.size());
    }

    /**
     * Orchestration Saga Step 3-1: cancel.delivery.command 발행
     * 파티션 키: orderId
     **/
    public void publishCancelDeliveryCommand(UUID orderId, UUID deliveryId) {
        kafkaTemplate.send(
                KafkaTopics.CANCEL_DELIVERY_COMMAND,
                orderId.toString(),
                CancelDeliveryCommand.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(orderId)
                        .deliveryId(deliveryId)
                        .build()
        );
        log.info("[cancel.delivery.command] 발행 orderId={}", orderId);
    }

    /**
     * Orchestration Saga Step 3-3 / 보상 Step 4-2: restore.stock.command 발행
     * 파티션 키: orderId
     **/
    public void publishRestoreStockCommand(UUID orderId, List<RestoreStockItemPayload> items) {
        kafkaTemplate.send(
                KafkaTopics.RESTORE_STOCK_COMMAND,
                orderId.toString(),
                RestoreStockCommand.builder()
                        .eventId(UUID.randomUUID())
                        .orderId(orderId)
                        .orderItems(items)
                        .build()
        );
        log.info("[restore.stock.command] 발행 orderId={} itemCount={}", orderId, items.size());
    }
}
