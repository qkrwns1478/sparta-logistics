package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: cancel.delivery.command
 * 발행: OrderService (Orchestrator) / 구독: DeliveryService
 * <p>
 * 주문 취소 Orchestration Saga Step 3-1
 * OrderService가 배송 취소를 명령함
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelDeliveryCommand {
    private UUID eventId;
    private UUID orderId;
    private UUID deliveryId;
}
