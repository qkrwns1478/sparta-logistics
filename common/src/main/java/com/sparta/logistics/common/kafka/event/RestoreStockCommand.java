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
 * 주문 취소 Orchestration Saga Step 3-3
 * OrderService가 재고 복구를 명령함
 * <p>
 * 파티션 키: orderId
 * */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RestoreStockCommand {
    private UUID eventId;
    private UUID orderId;
    // private UUID hubId; // RestoreStockItemPayload에 hubId가 추가되었으니 중복됩니다.
    // 복구할 항목 목록 (productId, quantity)
    private List<RestoreStockItemPayload> orderItems;
}
