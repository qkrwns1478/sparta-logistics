package com.sparta.logistics.hub.hubstock.service.helper;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubStockErrorCode;
import com.sparta.logistics.hub.hubstock.dto.request.AdjustHubStockRequest;
import com.sparta.logistics.hub.hubstock.dto.response.HubStockAdjustResponse;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import com.sparta.logistics.hub.hubstock.event.publisher.HubStockEventPublisher;
import com.sparta.logistics.hub.hubstock.repository.HubStockRepository;
import com.sparta.logistics.hub.hubstocklog.entity.HubStockLog;
import com.sparta.logistics.hub.hubstocklog.repository.HubStockLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubStockLockHelper {

    private final HubStockRepository hubStockRepository;
    private final HubStockLogRepository hubStockLogRepository;
    private final HubStockEventPublisher hubStockEventPublisher;

    // 새 트랜잭션 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HubStockAdjustResponse adjustWithOptimisticLock(UUID hubId, UUID stockId, AdjustHubStockRequest request) {

        HubStock hubStock = hubStockRepository.findByIdAndDeletedAtIsNull(stockId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        return adjust(hubStock, hubId, request);
    }

    // 새 트랜잭션 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HubStockAdjustResponse adjustWithPessimisticLock(UUID hubId, UUID stockId, AdjustHubStockRequest request) {

        HubStock hubStock = hubStockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        return adjust(hubStock, hubId, request);
    }

    private HubStockAdjustResponse adjust(HubStock hubStock, UUID hubId, AdjustHubStockRequest request) {

        if (!hubStock.getHub().getId().equals(hubId)) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND);
        }
        if (hubStock.getAvailable() + request.getChangeQuantity() < 0) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_INSUFFICIENT);
        }

        // 재고 변경 이력 생성
        int changeQuantity = request.getChangeQuantity();
        int beforeQuantity = hubStock.getAvailable();
        int afterQuantity = hubStock.getAvailable() + changeQuantity;
        hubStockLogRepository.save(HubStockLog.create(
                hubStock,
                changeQuantity,
                beforeQuantity,
                afterQuantity,
                request.getChangeType()
        ));

        hubStock.adjustAvailable(request.getChangeQuantity());

        // 재고 변경 후 Order Service 스냅샷 갱신을 위한 이벤트 등록 (커밋 후 발행)
        registerHubStockUpdatedEvent(hubStock);

        return HubStockAdjustResponse.from(hubStock, request.getChangeQuantity(), request.getChangeType());
    }

    private void registerHubStockUpdatedEvent(HubStock hubStock) {

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        hubStockEventPublisher.publishHubStockUpdated(
                                hubStock.getProductId(),
                                hubStock.getHub().getId(),
                                hubStock.getAvailable(),
                                hubStock.getVersion()
                        );
                    }
                }
        );
    }
}
