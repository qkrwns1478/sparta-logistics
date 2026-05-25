package com.sparta.logistics.delivery.infrastructure.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.delivery.client.HubServiceClient;
import com.sparta.logistics.delivery.client.UserServiceClient;
import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.dto.event.AiDeadlineCalculatedEvent;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventHandler {

    private final DeliveryService deliveryService;
    private final DeliveryEventPublisher eventPublisher;
    private final UserServiceClient userServiceClient;
    private final HubServiceClient hubServiceClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock.reserved", groupId = "delivery-service")
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
            eventPublisher.publishCreationFailed(event.orderId(), "INVALID_HUB_ID");
            return;
        }

        // user-service Feign 호출 — 트랜잭션 범위 밖
        // data() null 을 catch 바깥에서 명시적으로 체크: data()==null 은 NPE→USER_SERVICE_UNAVAILABLE 오분류 방지
        String slackId;
        try {
            var userResponse = userServiceClient.getUser(event.receiverId());
            if (userResponse.data() == null) {
                log.warn("[Kafka] slackId 없음(data=null) — receiverId={}, orderId={}", event.receiverId(), event.orderId());
                eventPublisher.publishCreationFailed(event.orderId(), "SLACK_ID_NOT_FOUND");
                return;
            }
            slackId = userResponse.data().slackId();
        } catch (Exception e) {
            log.warn("[Kafka] user-service 호출 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), "USER_SERVICE_UNAVAILABLE");
            return;
        }

        if (slackId == null) {
            log.warn("[Kafka] slackId 없음 — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), "SLACK_ID_NOT_FOUND");
            return;
        }

        // hub-service Feign 호출 — 트랜잭션 범위 밖 (경로 정보는 주문 완료 시점에 확정)
        List<HubRouteSegmentResponse> routeSegments;
        try {
            routeSegments = hubServiceClient.getRouteSegments(event.sourceHubId(), event.destinationHubId());
        } catch (Exception e) {
            log.warn("[Kafka] hub-service 호출 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), "HUB_SERVICE_UNAVAILABLE");
            return;
        }

        try {
            deliveryService.createDelivery(event, slackId, routeSegments);
            log.info("[Kafka] 배송 생성 완료 — orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("[Kafka] 배송 생성 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), "CREATE_FAILED");
        }
    }

    @KafkaListener(topics = "ai.deadline.calculated", groupId = "delivery-service")
    public void handleAiDeadlineCalculated(String message) {
        AiDeadlineCalculatedEvent event;
        try {
            event = objectMapper.readValue(message, AiDeadlineCalculatedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] ai.deadline.calculated 역직렬화 실패: {}", message, e);
            return;
        }
        deliveryService.updateFinalDispatchDeadline(event.deliveryId(), event.finalDispatchDeadlineAt());
        log.info("[Kafka] AI 발송 시한 업데이트 — deliveryId={}", event.deliveryId());
    }
}
