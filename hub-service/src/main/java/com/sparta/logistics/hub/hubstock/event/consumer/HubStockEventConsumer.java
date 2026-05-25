package com.sparta.logistics.hub.hubstock.event.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.hub.hubstock.event.dto.inbound.RestoreStockCommand;
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
}
