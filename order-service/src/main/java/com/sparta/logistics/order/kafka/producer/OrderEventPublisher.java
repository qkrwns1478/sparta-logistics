package com.sparta.logistics.order.kafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Choreography Saga Step 1-1: order.created 발행
     * 파티션 키: orderId
     **/
    public void publishOrderCreated(Order order, UUID sourceHubId, UUID destinationHubId, String receiverCompanyAddress) {
        List<OrderItemPayload> payloads = order.getOrderItems().stream()
                .map(item -> OrderItemPayload.builder()
                        .orderItemId(item.getId())
                        .productId(item.getProductId())
                        .quantity(item.getQuantity())
                        .hubId(item.getHubId())
                        .build())
                .toList();

        try {
            String message = objectMapper.writeValueAsString(
                    OrderCreatedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(order.getId())
                            .orderItems(payloads)
                            .requesterCompanyId(order.getRequesterCompanyId())
                            .receiverCompanyId(order.getReceiverCompanyId())
                            .sourceHubId(sourceHubId)
                            .destinationHubId(destinationHubId)
                            .receiverId(order.getRequesterUserId())
                            .deliveryAddress(receiverCompanyAddress)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.ORDER_CREATED, order.getId().toString(), message);
            log.info("[order.created] 발행 orderId={} itemCount={}", order.getId(), payloads.size());
        } catch (JsonProcessingException e) {
            log.error("[order.created] 직렬화 실패 orderId={}", order.getId(), e);
        }
    }

    /**
     * Orchestration Saga Step 3-1: cancel.delivery.command 발행
     * 파티션 키: orderId
     **/
    public void publishCancelDeliveryCommand(UUID orderId, UUID deliveryId) {
        try {
            String message = objectMapper.writeValueAsString(
                    CancelDeliveryCommand.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .deliveryId(deliveryId)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.CANCEL_DELIVERY_COMMAND, orderId.toString(), message);
            log.info("[cancel.delivery.command] 발행 orderId={}", orderId);
        } catch (JsonProcessingException e) {
            log.error("[cancel.delivery.command] 직렬화 실패 orderId={}", orderId, e);
        }
    }

    /**
     * Orchestration Saga Step 3-3 / 보상 Step 4-2: restore.stock.command 발행
     * 파티션 키: orderId
     **/
    public void publishRestoreStockCommand(UUID orderId, List<RestoreStockItemPayload> items) {
        try {
            String message = objectMapper.writeValueAsString(
                    RestoreStockCommand.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .orderItems(items)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.RESTORE_STOCK_COMMAND, orderId.toString(), message);
            log.info("[restore.stock.command] 발행 orderId={} itemCount={}", orderId, items.size());
        } catch (JsonProcessingException e) {
            log.error("[restore.stock.command] 직렬화 실패 orderId={}", orderId, e);
        }
    }
}
