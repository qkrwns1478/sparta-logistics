package com.sparta.logistics.delivery.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.AiDeadlineCalculatedEvent;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.delivery.client.HubServiceClient;
import com.sparta.logistics.delivery.client.UserServiceClient;
import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.dto.event.StockReservedItemPayload;
import com.sparta.logistics.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventHandler {

    private final DeliveryService deliveryService;
    private final DeliveryEventPublisher eventPublisher;
    private final UserServiceClient userServiceClient;
    private final HubServiceClient hubServiceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVED, groupId = "delivery-service")
    public void handleStockReserved(String message) {
        StockReservedEventDto event;
        try {
            event = objectMapper.readValue(message, StockReservedEventDto.class);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] stock.reserved 역직렬화 실패: {}", message, e);
            return;
        }

        if (event.sourceHubId() == null || event.destinationHubId() == null) {
            log.warn("[Kafka] 허브 ID null — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "INVALID_HUB_ID",
                    toRestoreItems(event.orderItems()));
            return;
        }

        // user-service Feign 호출 — 트랜잭션 범위 밖
        // data() null 을 catch 바깥에서 명시적으로 체크: data()==null 은 NPE→USER_SERVICE_UNAVAILABLE 오분류 방지
        String slackId;
        try {
            var userResponse = userServiceClient.getUser(event.receiverId());
            if (userResponse.data() == null) {
                log.warn("[Kafka] slackId 없음(data=null) — receiverId={}, orderId={}", event.receiverId(), event.orderId());
                eventPublisher.publishCreationFailed(event.orderId(), null, "SLACK_ID_NOT_FOUND",
                        toRestoreItems(event.orderItems()));
                return;
            }
            slackId = userResponse.data().slackId();
        } catch (Exception e) {
            log.warn("[Kafka] user-service 호출 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), null, "USER_SERVICE_UNAVAILABLE",
                    toRestoreItems(event.orderItems()));
            return;
        }

        if (slackId == null) {
            log.warn("[Kafka] slackId 없음 — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "SLACK_ID_NOT_FOUND",
                    toRestoreItems(event.orderItems()));
            return;
        }

        // hub-service Feign 호출 — 트랜잭션 범위 밖 (경로 정보는 주문 완료 시점에 확정)
        List<HubRouteSegmentResponse> routeSegments;
        try {
            routeSegments = hubServiceClient.getRouteSegments(event.sourceHubId(), event.destinationHubId());
        } catch (Exception e) {
            log.warn("[Kafka] hub-service 호출 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), null, "HUB_SERVICE_UNAVAILABLE",
                    toRestoreItems(event.orderItems()));
            return;
        }

        try {
            deliveryService.createDelivery(event, slackId, routeSegments);
            log.info("[Kafka] 배송 생성 완료 — orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("[Kafka] 배송 생성 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), null, "CREATE_FAILED",
                    toRestoreItems(event.orderItems()));
        }
    }

    @KafkaListener(topics = KafkaTopics.AI_DEADLINE_CALCULATED, groupId = "delivery-service")
    public void handleAiDeadlineCalculated(String message) {
        AiDeadlineCalculatedEvent event;
        try {
            event = objectMapper.readValue(message, AiDeadlineCalculatedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] ai.deadline.calculated 역직렬화 실패: {}", message, e);
            return;
        }
        deliveryService.updateFinalDispatchDeadline(event.getDeliveryId(), event.getFinalDispatchDeadlineAt());
        log.info("[Kafka] AI 발송 시한 업데이트 — deliveryId={}", event.getDeliveryId());
    }

    private List<RestoreStockItemPayload> toRestoreItems(List<StockReservedItemPayload> items) {
        if (items == null) return Collections.emptyList();
        return items.stream()
                .map(i -> RestoreStockItemPayload.builder()
                        .productId(i.productId())
                        .quantity(i.reservedQuantity())
                        .build())
                .collect(Collectors.toList());
    }
}
