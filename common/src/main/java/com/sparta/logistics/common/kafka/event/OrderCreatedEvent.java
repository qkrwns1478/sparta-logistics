package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 토픽: order.created
 * 발행: OrderService / 구독: HubService
 * <p>
 * 주문 생성이 완료되면 발행함
 * HubService는 이 이벤트를 받아 각 상품의 재고를 예약함
 * <p>
 * 파티션 키: orderId (동일 주문의 이벤트 순서 보장)
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreatedEvent {
    private UUID orderId;
    // 주문 항목 목록 (productId, quantity, hubId)
    private List<OrderItemPayload> orderItems;
    // 요청 업체 ID
    private UUID requesterCompanyId;
    // 수령 업체 소속 허브 ID
    private UUID destinationHubId;
}
