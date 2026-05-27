package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: stock.restoration.failed
 * 발행: HubService / 구독: OrderService (Orchestrator)
 * <p>
 * 주문 취소 Orchestration Saga 보상 Step 4-2
 * HubService가 재고 복구에 실패했을 때 발행함
 * OrderService Orchestrator는 이를 수신해서 restore.stock.command를 재발행함
 * <p>
 * 파티션 키: orderId
 **/
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRestorationFailedEvent {
    private UUID eventId;
    private UUID orderId;
    private String reason;
}
