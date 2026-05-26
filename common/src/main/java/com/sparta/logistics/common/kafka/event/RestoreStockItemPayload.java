package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** RestoreStockCommand 내 복구 대상 개별 항목 페이로드 **/
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreStockItemPayload {
    private UUID productId;
    private UUID hubId;
    // 복구할 수량
    private Integer quantity;
}
