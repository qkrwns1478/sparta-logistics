package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 토픽: restore.stock.command
 * 발행: OrderService (Orchestrator) / 구독: HubService
 * <p>
 * 주문 취소 Orchestration Saga Step 3
 * OrderService가 재고 복구를 명령함
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreStockCommand {
    // 메시지 고유 ID — Idempotent Consumer의 중복 소비 방지에 사용
    private UUID eventId;
    private UUID orderId;
    // 복구할 항목 목록 (productId, quantity)
    private List<RestoreStockItemPayload> orderItems;
}
