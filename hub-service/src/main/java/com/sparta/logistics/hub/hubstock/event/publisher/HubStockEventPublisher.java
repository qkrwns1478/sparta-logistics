package com.sparta.logistics.hub.hubstock.event.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.hub.hubstock.event.dto.outbound.StockReservationFailedEvent;
import com.sparta.logistics.hub.hubstock.event.dto.outbound.StockReservedEvent;
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

    public void publishStockReservationFailed(UUID orderId, UUID productId, String reason) {

        try {
            StockReservationFailedEvent event = new StockReservationFailedEvent(orderId, productId, reason);
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send("stock.reservation.failed", message);

            log.info("[Kafka] stock.reservation.failed 발행 - orderId: {}, productId: {}", orderId, productId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] stock.reservation.failed 발행 실패 - orderId: {}", orderId, e);
        }
    }

    public void publishStockReserved(StockReservedEvent event) {

        try {
            String message = objectMapper.writeValueAsString(event);

            kafkaTemplate.send("stock.reserved", message);

            log.info("[Kafka] stock.reserved 발행 - orderId: {}", event.getOrderId());
        } catch (JsonProcessingException e) {
            log.error("[Kafka] stock.reserved 발행 실패 - orderId: {}", event.getOrderId(), e);
        }
    }
}
