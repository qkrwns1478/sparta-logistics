package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** DeliveryStartedEvent 내 배송 출고 대상 상품 항목 페이로드 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryOrderItemPayload {
    // 주문 항목 ID (HubService 재고 변경 이력 기록용)
    private UUID orderItemId;
    private UUID productId;
    private UUID hubId;
    private Integer quantity;
}
