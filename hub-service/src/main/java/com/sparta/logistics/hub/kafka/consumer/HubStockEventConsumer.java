package com.sparta.logistics.hub.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.OrderCreatedEvent;
import com.sparta.logistics.common.kafka.event.RestoreStockCommand;
import com.sparta.logistics.hub.hubstock.service.HubStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubStockEventConsumer {

    private final ObjectMapper objectMapper;
    private final HubStockService hubStockService;

    @KafkaListener(topics = "restore.stock.command", groupId = "hub-service")
    public void consumeRestoreStockCommand(String message) {

        try {
            RestoreStockCommand command = objectMapper.readValue(message, RestoreStockCommand.class);

            log.info("[Kafka] restore.stock.command 수신 - orderId: {}", command.getOrderId());

            hubStockService.restoreStock(command);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] restore.stock.command 역직렬화 실패 - message: {}", message, e);
        }
    }

    @KafkaListener(topics = "delivery.creation.failed", groupId = "hub-service")
    public void consumeDeliveryCreationFailed(String message) {

        // todo: delivery.creation.failed order_items가 포함되어야 재고 복구 처리 가능
        log.info("[Kafka] delivery.creation.failed 수신 - message: {}", message);
    }

    @KafkaListener(topics = "order.created", groupId = "hub-service")
    public void consumeOrderCreated(String message) {

        try {
            OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);

            log.info("[Kafka] order.created 수신 - orderId: {}", event.getOrderId());

            hubStockService.reserveStock(event);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] order.created 역직렬화 실패 - message: {}", message, e);
        }
    }
}
