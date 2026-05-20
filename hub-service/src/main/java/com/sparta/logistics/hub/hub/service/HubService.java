package com.sparta.logistics.hub.hub.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubErrorCode;
import com.sparta.logistics.hub.hub.dto.request.ReqCreateHubDto;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubService {

    private final HubRepository hubRepository;

    @Transactional
    public Hub createHub(ReqCreateHubDto request) {

        // 허브 이름 중복 체크
        if (hubRepository.existsByName(request.getName())) {
            throw new BusinessException(HubErrorCode.HUB_NAME_DUPLICATED);
        }

        Hub hub = Hub.create(
                request.getName(),
                request.getAddress(),
                request.getLatitude(),
                request.getLongitude()
        );

        return hubRepository.save(hub);
    }

    @Transactional(readOnly = true)
    public Hub getHub(UUID hubId) {

        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }

    public boolean existsHub(UUID hubId) {

        return hubRepository.existsById(hubId);
    }
}
