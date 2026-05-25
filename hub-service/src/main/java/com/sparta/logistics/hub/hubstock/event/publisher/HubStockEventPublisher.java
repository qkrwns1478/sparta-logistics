package com.sparta.logistics.hub.hubstock.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.hub.hubstock.event.dto.outbound.StockRestoredAckEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class HubStockEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishStockRestoredAck(UUID orderId) {

        try {
            StockRestoredAckEvent event = new StockRestoredAckEvent(orderId);
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send("stock.restored.ack", message);

            log.info("[Kafka] stock.restored.ack 발행 - orderId: {}", orderId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] stock.restored.ack 발행 실패 - orderId: {}", orderId, e);
        }
    }
}
