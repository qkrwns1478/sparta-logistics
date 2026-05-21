package com.sparta.logistics.hub.hubstock.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubErrorCode;
import com.sparta.logistics.hub.exception.HubStockErrorCode;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import com.sparta.logistics.hub.hubstock.dto.request.CreateHubStockRequest;
import com.sparta.logistics.hub.hubstock.dto.response.HubStockCreateResponse;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import com.sparta.logistics.hub.hubstock.repository.HubStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubStockService {

    private final HubStockRepository hubStockRepository;
    private final HubRepository hubRepository;

    // todo: product도 존재 여부를 체크 고민
    @Transactional
    public HubStockCreateResponse createHubStock(UUID hubId, CreateHubStockRequest request) {

        Hub hub = hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        // 중복 체크
        if (hubStockRepository.existsByHubAndProductId(hub, request.getProductId())) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_ALREADY_EXISTS);
        }

        // 레이스 컨디션 처리
        try {
            HubStock hubStock = HubStock.create(hub, request.getProductId(), request.getInitialQuantity());
            HubStock savedStock = hubStockRepository.save(hubStock);
            hubStockRepository.flush();
            return HubStockCreateResponse.from(savedStock);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HubStockErrorCode.HUB_STOCK_ALREADY_EXISTS);
        }
    }
}
