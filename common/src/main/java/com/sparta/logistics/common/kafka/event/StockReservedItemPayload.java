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
    private UUID productId;
    //실제로 예약된 수량
    private Integer reservedQuantity;
}
