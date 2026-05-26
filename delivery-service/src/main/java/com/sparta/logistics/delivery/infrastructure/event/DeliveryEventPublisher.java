package com.sparta.logistics.delivery.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCreated(UUID deliveryId, UUID orderId,
                               UUID sourceHubId, UUID destinationHubId,
                               UUID companyDeliveryManagerId) {
        try {
            String message = objectMapper.writeValueAsString(
                    DeliveryCreatedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .deliveryId(deliveryId)
                            .orderId(orderId)
                            .sourceHubId(sourceHubId)
                            .destinationHubId(destinationHubId)
                            .companyDeliveryManagerId(companyDeliveryManagerId)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.DELIVERY_CREATED, message);
            log.info("[Kafka] delivery.created 발행 — deliveryId={}, orderId={}", deliveryId, orderId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.created 직렬화 실패 — deliveryId={}", deliveryId, e);
        }
    }

    public void publishCreationFailed(UUID orderId, UUID deliveryId,
                                      String reason, List<RestoreStockItemPayload> itemsToRestore) {
        try {
            String message = objectMapper.writeValueAsString(
                    DeliveryCreationFailedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .deliveryId(deliveryId)
                            .reason(reason)
                            .itemsToRestore(itemsToRestore)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.DELIVERY_CREATION_FAILED, message);
            log.info("[Kafka] delivery.creation.failed 발행 — orderId={}, reason={}", orderId, reason);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.creation.failed 직렬화 실패 — orderId={}", orderId, e);
        }
    }
}
