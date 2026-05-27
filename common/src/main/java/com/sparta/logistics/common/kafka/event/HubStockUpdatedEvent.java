package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: hub.stock.updated
 * 발행: HubService / 구독: OrderService
 * <p>
 * 재고 변경이 발생할 때마다 발행함
 * OrderService는 이를 수신해 ProductStockSnapshot을 갱신함
 * hubStockVersion으로 구버전 이벤트를 무시해 멱등성을 보장함
 * <p>
 * 파티션 키: productId (상품 단위 순서 보장)
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HubStockUpdatedEvent {
    private UUID eventId;
    private UUID productId;
    private UUID hubId;
    // 변경 후 가용 재고
    private Integer available;
    // HubStock 엔티티의 @Version 값 (구버전 이벤트 필터링에 사용)
    private Long hubStockVersion;
}
