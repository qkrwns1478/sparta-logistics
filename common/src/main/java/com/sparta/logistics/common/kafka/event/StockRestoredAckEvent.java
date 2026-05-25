package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 토픽: stock.restored.ack
 * 발행: HubService / 구독: OrderService (Orchestrator)
 * <p>
 * 주문 취소 Orchestration Saga Step 4
 * HubService가 재고 복구를 완료하고 응답함
 * OrderService Orchestrator는 이를 수신한 뒤 주문 상태를 CANCELLED로 확정함
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockRestoredAckEvent {
    private UUID orderId;
}
