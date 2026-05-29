package com.sparta.logistics.hub.hubstock.service;

import com.sparta.logistics.common.kafka.event.RestoreStockItemPayload;
import com.sparta.logistics.hub.hubstock.service.helper.HubStockLockHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubStockCompensator {

    private final HubStockLockHelper hubStockLockHelper;

    // 독립 트랜잭션으로 실행 — 호출자 롤백과 무관하게 보상 커밋
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensateRestoredItems(List<RestoreStockItemPayload> items) {
        for (RestoreStockItemPayload item : items) {
            try {
                executeWithLock(
                        () -> { hubStockLockHelper.compensateRestoreWithOptimisticLock(
                                item.getHubId(), item.getProductId(),
                                item.getQuantity(), item.getOrderItemId()
                        ); return null; },
                        () -> { hubStockLockHelper.compensateRestoreWithPessimisticLock(
                                item.getHubId(), item.getProductId(),
                                item.getQuantity(), item.getOrderItemId()
                        ); return null; }
                );
            } catch (Exception e) {
                log.error("[HubStock][수동처리 필요] 보상 트랜잭션 실패. hubId={}, productId={}, quantity={}",
                        item.getHubId(), item.getProductId(), item.getQuantity(), e);
            }
        }
    }

    private <T> T executeWithLock(Supplier<T> optimistic, Supplier<T> pessimistic) {
        try {
            return optimistic.get();
        } catch (Exception e) {
            return pessimistic.get();
        }
    }
}
