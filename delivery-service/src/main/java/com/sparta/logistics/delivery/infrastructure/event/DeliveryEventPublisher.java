package com.sparta.logistics.delivery.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.delivery.dto.event.DeliveryCreatedEvent;
import com.sparta.logistics.delivery.dto.event.DeliveryCreationFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCreated(UUID deliveryId, UUID orderId) {
        try {
            String message = objectMapper.writeValueAsString(
                    new DeliveryCreatedEvent(deliveryId, orderId)
            );
            kafkaTemplate.send("delivery.created", message);
            log.info("[Kafka] delivery.created 발행 — deliveryId={}, orderId={}", deliveryId, orderId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.created 직렬화 실패 — deliveryId={}", deliveryId, e);
        }
    }

    public void publishCreationFailed(UUID orderId, String reason) {
        try {
            String message = objectMapper.writeValueAsString(
                    new DeliveryCreationFailedEvent(orderId, reason)
            );
            kafkaTemplate.send("delivery.creation.failed", message);
            log.info("[Kafka] delivery.creation.failed 발행 — orderId={}, reason={}", orderId, reason);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.creation.failed 직렬화 실패 — orderId={}", orderId, e);
        }
    }
}
