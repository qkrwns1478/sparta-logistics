package com.sparta.logistics.delivery.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.AiDeadlineCalculatedEvent;
import com.sparta.logistics.common.kafka.event.CancelDeliveryCommand;
import com.sparta.logistics.common.kafka.event.HubDeletedEvent;
import com.sparta.logistics.delivery.service.DeliveryManagerService;
import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.client.FeignCallService;
import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.client.response.UserResponse;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.dto.event.StockReservedItemPayload;
import com.sparta.logistics.delivery.kafka.producer.DeliveryEventPublisher;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.service.DeliveryService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryEventHandler {

    private final DeliveryService deliveryService;
    private final DeliveryManagerService deliveryManagerService;
    private final DeliveryEventPublisher eventPublisher;
    private final FeignCallService feignCallService;
    private final ObjectMapper objectMapper;
    private final DeliveryRepository deliveryRepository;

    @KafkaListener(topics = KafkaTopics.STOCK_RESERVED, groupId = "delivery-service")
    public void handleStockReserved(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        StockReservedEventDto event;
        try {
            event = objectMapper.readValue(message, StockReservedEventDto.class);
        } catch (JsonProcessingException e) {
            // 역직렬화 실패는 재시도해도 의미 없음 — 의도적 offset 커밋
            // orderId 추출 불가 → publishCreationFailed 미호출 (보상 불가, 수동 처리 필요)
            log.error("[Kafka][수동처리 필요] stock.reserved 역직렬화 실패 — topic={}, partition={}, offset={}",
                    topic, partition, offset, e);
            return;
        }

        if (event.sourceHubId() == null || event.destinationHubId() == null) {
            log.warn("[Kafka] 허브 ID null — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "INVALID_HUB_ID",
                    toRestoreItems(event.orderItems()));
            return;
        }

        // 멱등성 조기 반환: Feign 호출 이전에 중복 체크 → 재전달 시 불필요한 외부 호출 및 false publishCreationFailed 방지
        if (deliveryRepository.existsByOrderIdAndSourceHubId(event.orderId(), event.sourceHubId())) {
            log.info("[Kafka] 이미 처리된 주문+허브 — 스킵 orderId={}", event.orderId());
            return;
        }

        String systemUserId = java.util.UUID.randomUUID().toString();


        // user-service Feign 호출 — 3회 retry 후 실패 시 BusinessException (CB 내부 예외 포함)
        ApiResponse<UserResponse> userResponse;
        try {
            userResponse = feignCallService.fetchUser(event.receiverId(), systemUserId, "MASTER");
        } catch (Exception e) {
            log.warn("[Kafka] user-service 호출 실패 — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "USER_SERVICE_UNAVAILABLE",
                    toRestoreItems(event.orderItems()));
            return;
        }

        if (userResponse.data() == null) {
            log.warn("[Kafka] slackId 없음(data=null) — receiverId={}, orderId={}", event.receiverId(), event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "SLACK_ID_NOT_FOUND",
                    toRestoreItems(event.orderItems()));
            return;
        }

        String slackId = userResponse.data().slackId();
        if (slackId == null) {
            log.warn("[Kafka] slackId 없음 — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "SLACK_ID_NOT_FOUND",
                    toRestoreItems(event.orderItems()));
            return;
        }

        // hub-service Feign 호출 — 3회 retry 후 실패 시 BusinessException (CB 내부 예외 포함)
        List<HubRouteSegmentResponse> routeSegments;
        try {
            routeSegments = feignCallService.fetchRouteSegments(event.sourceHubId(), event.destinationHubId(), systemUserId, "MASTER");
        } catch (Exception e) {
            log.warn("[Kafka] hub-service 호출 실패 — orderId={}", event.orderId());
            eventPublisher.publishCreationFailed(event.orderId(), null, "HUB_SERVICE_UNAVAILABLE",
                    toRestoreItems(event.orderItems()));
            return;
        }

        try {
            deliveryService.createDelivery(event, slackId, routeSegments);
            log.info("[Kafka] 배송 생성 완료 — orderId={}", event.orderId());
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 저장 경쟁: 다른 스레드가 먼저 처리 완료 — 정상 상황, 사가 보상 불필요
            log.info("[Kafka] 중복 저장 경쟁 — 이미 처리됨 orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("[Kafka] 배송 생성 실패 — orderId={}", event.orderId(), e);
            eventPublisher.publishCreationFailed(event.orderId(), null, "CREATE_FAILED",
                    toRestoreItems(event.orderItems()));
        }
    }

    @KafkaListener(topics = KafkaTopics.CANCEL_DELIVERY_COMMAND, groupId = "delivery-service")
    public void handleCancelDeliveryCommand(String message) {
        CancelDeliveryCommand command;
        try {
            command = objectMapper.readValue(message, CancelDeliveryCommand.class);
        } catch (JsonProcessingException e) {
            log.error("[Kafka] cancel.delivery.command 역직렬화 실패: {}", message, e);
            return;
        }

        try {
            boolean cancelled = deliveryService.cancelDeliveryByCommand(command.getDeliveryId());
            if (cancelled) {
                eventPublisher.publishCancelledAck(command.getDeliveryId(), command.getOrderId());
            } else {
                eventPublisher.publishCancellationFailed(command.getOrderId(), command.getDeliveryId(), "DELIVERY_IN_TRANSIT");
            }
        } catch (Exception e) {
            log.error("[Kafka] 배송 취소 처리 실패 — orderId={}", command.getOrderId(), e);
            eventPublisher.publishCancellationFailed(command.getOrderId(), command.getDeliveryId(), "CANCEL_FAILED");
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
        try {
            deliveryService.updateFinalDispatchDeadline(event.getDeliveryId(), event.getFinalDispatchDeadlineAt());
            log.info("[Kafka] AI 발송 시한 업데이트 — deliveryId={}", event.getDeliveryId());
        } catch (BusinessException e) {
            log.warn("[Kafka] AI 발송 시한 업데이트 실패(비즈니스) — deliveryId={}, code={}", event.getDeliveryId(), e.getErrorCode());
        } catch (DataAccessException e) {
            log.error("[Kafka] AI 발송 시한 업데이트 실패(DB) — deliveryId={}, 메시지 스킵", event.getDeliveryId(), e);
        } catch (Exception e) {
            log.error("[Kafka] AI 발송 시한 업데이트 실패(기타) — deliveryId={}, 메시지 스킵", event.getDeliveryId(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.HUB_DELETED, groupId = "delivery-service")
    public void handleHubDeleted(String message) {
        HubDeletedEvent event;
        try {
            event = objectMapper.readValue(message, HubDeletedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[Kafka][수동처리 필요] hub.deleted 역직렬화 실패 — message={}", message, e);
            return;
        }
        try {
            deliveryManagerService.softDeleteManagersByHubId(event.getHubId(), event.getDeletedBy());
        } catch (Exception e) {
            log.error("[Kafka][수동처리 필요] hub.deleted 처리 실패 — hubId={}", event.getHubId(), e);
            throw new RuntimeException(e);
        }
    }

    private List<RestoreStockItemPayload> toRestoreItems(List<StockReservedItemPayload> items) {
        if (items == null) return Collections.emptyList();
        return items.stream()
                .map(i -> RestoreStockItemPayload.builder()
                        .orderItemId(i.orderItemId())
                        .productId(i.productId())
                        .hubId(i.sourceHubId())
                        .quantity(i.reservedQuantity())
                        .build())
                .toList();
    }
}
