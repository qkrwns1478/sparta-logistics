package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** StockReservedEvent 내 예약 완료된 개별 항목 페이로드 **/
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservedItemPayload {
    // 원래 주문 항목 ID (DeliveryOrderItemEntity → delivery.started 페이로드까지 전파)
    private UUID orderItemId;
    private UUID productId;
    // 실제로 예약된 수량
    private Integer reservedQuantity;
    // 상품 재고를 보유한 출발 허브 ID (아이템마다 허브가 다를 수 있음)
    private UUID sourceHubId;
}
