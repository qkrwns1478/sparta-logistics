package com.sparta.logistics.delivery.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCancelledAckEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCancellationFailedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryOrderItemPayload;
import com.sparta.logistics.common.kafka.event.DeliveryStartedEvent;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.delivery.entity.DeliveryOrderItemEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
            kafkaTemplate.send(KafkaTopics.DELIVERY_CREATED, deliveryId.toString(), message);
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
            kafkaTemplate.send(KafkaTopics.DELIVERY_CREATION_FAILED, orderId.toString(), message);
            log.info("[Kafka] delivery.creation.failed 발행 — orderId={}, reason={}", orderId, reason);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.creation.failed 직렬화 실패 — orderId={}", orderId, e);
        }
    }

    public void publishStarted(UUID deliveryId, UUID orderId, List<DeliveryOrderItemEntity> items) {
        List<DeliveryOrderItemPayload> payloads = items.stream()
                .map(i -> DeliveryOrderItemPayload.builder()
                        .productId(i.getProductId())
                        .quantity(i.getQuantity())
                        .build())
                .collect(Collectors.toList());
        try {
            String message = objectMapper.writeValueAsString(
                    DeliveryStartedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .deliveryId(deliveryId)
                            .orderId(orderId)
                            .orderItems(payloads)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.DELIVERY_STARTED, deliveryId.toString(), message);
            log.info("[Kafka] delivery.started 발행 — deliveryId={}", deliveryId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.started 직렬화 실패 — deliveryId={}", deliveryId, e);
        }
    }

    public void publishCancelledAck(UUID deliveryId, UUID orderId) {
        try {
            String message = objectMapper.writeValueAsString(
                    DeliveryCancelledAckEvent.builder()
                            .eventId(UUID.randomUUID())
                            .deliveryId(deliveryId)
                            .orderId(orderId)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.DELIVERY_CANCELLED_ACK, orderId.toString(), message);
            log.info("[Kafka] delivery.cancelled.ack 발행 — orderId={}", orderId);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.cancelled.ack 직렬화 실패 — deliveryId={}", deliveryId, e);
        }
    }

    public void publishCancellationFailed(UUID orderId, UUID deliveryId, String reason) {
        try {
            String message = objectMapper.writeValueAsString(
                    DeliveryCancellationFailedEvent.builder()
                            .eventId(UUID.randomUUID())
                            .orderId(orderId)
                            .deliveryId(deliveryId)
                            .reason(reason)
                            .build()
            );
            kafkaTemplate.send(KafkaTopics.DELIVERY_CANCELLATION_FAILED, orderId.toString(), message);
            log.info("[Kafka] delivery.cancellation.failed 발행 — orderId={}, reason={}", orderId, reason);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] delivery.cancellation.failed 직렬화 실패 — orderId={}", orderId, e);
        }
    }
}
