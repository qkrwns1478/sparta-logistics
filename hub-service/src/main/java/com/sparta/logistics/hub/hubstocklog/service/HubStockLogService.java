package com.sparta.logistics.hub.hubstocklog.service;

import com.sparta.logistics.hub.hubstock.enums.HubStockChangeType;
import com.sparta.logistics.hub.hubstocklog.dto.response.HubStockLogListResponse;
import com.sparta.logistics.hub.hubstocklog.repository.HubStockLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubStockLogService {

    private final HubStockLogRepository hubStockLogRepository;

    @Transactional(readOnly = true)
    public Page<HubStockLogListResponse> getHubStockLogList(
            UUID hubId, UUID stockId, HubStockChangeType changeType, Pageable pageable) {

        return hubStockLogRepository.findAllByCondition(stockId, hubId, changeType, pageable)
                .map(log -> HubStockLogListResponse.from(log, stockId));
    }
}
