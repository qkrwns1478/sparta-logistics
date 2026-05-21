package com.sparta.logistics.hub.hubstock.helper;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubStockErrorCode;
import com.sparta.logistics.hub.hubstock.dto.request.AdjustHubStockRequest;
import com.sparta.logistics.hub.hubstock.dto.response.HubStockAdjustResponse;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import com.sparta.logistics.hub.hubstock.repository.HubStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HubStockLockHelper {

    private final HubStockRepository hubStockRepository;

    // 새 트랜잭션 생성
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public HubStockAdjustResponse adjustWithPessimisticLock(UUID hubId, UUID stockId, AdjustHubStockRequest request) {

        HubStock lockedStock = hubStockRepository.findByIdWithLock(stockId)
                .orElseThrow(() -> new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND));

        if (!lockedStock.getHub().getId().equals(hubId)) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_NOT_FOUND);
        }

        if (lockedStock.getAvailable() + request.getChangeQuantity() < 0) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_INSUFFICIENT);
        }

        lockedStock.adjustAvailable(request.getChangeQuantity());
        return HubStockAdjustResponse.from(lockedStock, request.getChangeQuantity(), request.getChangeType());
    }
}
