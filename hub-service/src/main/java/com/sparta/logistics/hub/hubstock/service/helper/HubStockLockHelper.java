package com.sparta.logistics.hub.hubstock.service.helper;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubStockErrorCode;
import com.sparta.logistics.hub.hubstock.dto.request.AdjustHubStockRequest;
import com.sparta.logistics.hub.hubstock.dto.response.HubStockAdjustResponse;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import com.sparta.logistics.hub.hubstock.enums.HubStockChangeType;
import com.sparta.logistics.hub.kafka.publisher.HubStockEventPublisher;
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


    // ========================
    // MANUAL_ADJUST
    // ========================


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
                null,
                null,
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

    // ========================
    // 재고 예약 (ORDER_RESERVE)
    // ========================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveWithOptimisticLock(UUID hubId, UUID productId, int quantity, UUID orderItemId) {

        HubStock hubStock = hubStockRepository.findByHubIdAndProductId(hubId, productId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        reserve(hubStock, quantity, orderItemId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveWithPessimisticLock(UUID hubId, UUID productId, int quantity, UUID orderItemId) {
        HubStock hubStock = hubStockRepository.findByHubIdAndProductIdWithLock(hubId, productId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        reserve(hubStock, quantity, orderItemId);
    }

    private void reserve(HubStock hubStock, int quantity, UUID orderItemId) {

        if (hubStock.getAvailable() < quantity) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_INSUFFICIENT);
        }

        int beforeQuantity = hubStock.getAvailable();
        hubStock.reserve(quantity);
        int afterQuantity = hubStock.getAvailable();

        hubStockLogRepository.save(HubStockLog.create(
                hubStock,
                orderItemId,
                null,
                -quantity,
                beforeQuantity,
                afterQuantity,
                HubStockChangeType.ORDER_RESERVE
        ));

        registerHubStockUpdatedEvent(hubStock);
    }

    // ========================
    // 재고 복구 (CANCEL_RESTORE)
    // ========================

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreWithOptimisticLock(UUID hubId, UUID productId, int quantity, UUID orderItemId, UUID deliveryId) {

        HubStock hubStock = hubStockRepository.findByHubIdAndProductId(hubId, productId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        restore(hubStock, quantity, orderItemId, deliveryId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void restoreWithPessimisticLock(UUID hubId, UUID productId, int quantity, UUID orderItemId, UUID deliveryId) {

        HubStock hubStock = hubStockRepository.findByHubIdAndProductIdWithLock(hubId, productId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        restore(hubStock, quantity, orderItemId, deliveryId);
    }

    private void restore(HubStock hubStock, int quantity, UUID orderItemId, UUID deliveryId) {

        int beforeQuantity = hubStock.getAvailable();
        hubStock.restore(quantity);
        int afterQuantity = hubStock.getAvailable();

        hubStockLogRepository.save(HubStockLog.create(
                hubStock, orderItemId, deliveryId,
                quantity, beforeQuantity, afterQuantity,
                HubStockChangeType.CANCEL_RESTORE
        ));

        registerHubStockUpdatedEvent(hubStock);
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
