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
 * 재고 예약이 완료되면 발행한다.
 * DeliveryService는 이 이벤트를 받아 배송 및 경로를 생성한다.
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedEvent {
    private UUID orderId;
    // 출발 허브 ID (상품 재고를 가진 허브)
    private UUID sourceHubId;
    // 도착 허브 ID (수령 업체 소속 허브)
    private UUID destinationHubId;
    // 예약 완료된 항목 목록
    private List<StockReservedItemPayload> orderItems;
}
