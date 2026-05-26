package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 토픽: stock.reserved
 * 발행: HubService / 구독: DeliveryService
 * <p>
 * 재고 예약이 완료되면 발행함
 * DeliveryService는 이 이벤트를 받아 배송 및 경로를 생성함
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {
    private UUID eventId;
    private UUID orderId;
    // 도착 허브 ID (수령 업체 소속 허브)
    private UUID destinationHubId;
    // 예약 완료된 항목 목록 (각 항목이 sourceHubId를 개별로 보유)
    private List<StockReservedItemPayload> orderItems;
}
