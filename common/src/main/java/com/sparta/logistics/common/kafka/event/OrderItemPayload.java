package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * OrderCreatedEvent 내 주문 항목 페이로드
 * productId, quantity, hubId를 담아 HubService가 재고를 특정할 수 있게 함
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemPayload {
    // 상품 ID
    private UUID productId;
    // 주문 수량
    private Integer quantity;
    // 상품이 속한 허브 ID (재고 예약 대상 허브)
    private UUID hubId;
}
