package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 토픽: delivery.creation.failed
 * 발행: DeliveryService / 구독: HubService, OrderService
 * <p>
 * 배송 생성에 실패하면 발행함 (Choreography 보상)
 * - HubService: 재고 예약 복구 (CANCEL_RESTORE)
 * - OrderService: 주문을 CANCELLED 처리
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCreationFailedEvent {
    private UUID eventId;
    private UUID orderId;
    // 생성 실패한 배송 ID (생성 자체가 실패한 경우 null)
    private UUID deliveryId;
    private String reason;
    // 해당 허브에서 복구할 상품 List
    private List<RestoreStockItemPayload> itemsToRestore;
}