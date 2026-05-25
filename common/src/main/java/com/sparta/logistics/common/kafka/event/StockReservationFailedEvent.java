package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: stock.reservation.failed
 * 발행: HubService / 구독: OrderService
 * <p>
 * 재고 부족 등으로 예약에 실패했을 때 발행함
 * OrderService는 이 이벤트를 받아 주문을 CANCELLED 처리함 (Choreography 보상)
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReservationFailedEvent {
    private UUID orderId;
    //예약 실패한 상품 ID
    private UUID productId;
    // 실패 사유
    private String reason;
}
