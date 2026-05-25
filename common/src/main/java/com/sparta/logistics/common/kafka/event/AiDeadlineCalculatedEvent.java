package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 토픽: ai.deadline.calculated
 * 발행: SlackService(NotificationService) / 구독: DeliveryService
 * <p>
 * AI가 발송 시한을 산출하면 발행함
 * DeliveryService는 이를 수신해 Delivery에 finalDeadlineAt을 저장함
 * <p>
 * 파티션 키: deliveryId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDeadlineCalculatedEvent {
    private UUID deliveryId;
    private UUID orderId;
    // AI가 산출한 최종 발송 시한
    private LocalDateTime finalDeadlineAt;
}
