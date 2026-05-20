package com.sparta.logistics.hub.hub.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubErrorCode;
import com.sparta.logistics.hub.hub.dto.request.CreateHubRequest;
import com.sparta.logistics.hub.hub.dto.request.UpdateHubRequest;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubService {

    private final HubRepository hubRepository;


    @Transactional
    public Hub createHub(CreateHubRequest request) {

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

        // 동시 요청으로 인한 레이스 컨디션 처리
        try {
            Hub savedHub = hubRepository.save(hub);
            hubRepository.flush();
            return savedHub;
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HubErrorCode.HUB_NAME_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public Hub getHub(UUID hubId) {

        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public boolean existsHub(UUID hubId) {

        return hubRepository.existsById(hubId);
    }

    @Transactional
    public Hub updateHub(UUID hubId, UpdateHubRequest request) {

        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        // 허브 이름 중복 본인 제외 체크
        if (hubRepository.existsByNameAndIdNot(request.getName(), hubId)) {
            throw new BusinessException(HubErrorCode.HUB_NAME_DUPLICATED);
        }

        hub.update(
                request.getName(),
                request.getAddress(),
                request.getLatitude(),
                request.getLongitude(),
                request.getStatus()
        );

        // 동시 요청으로 인한 레이스 컨디션 처리
        try {
            hubRepository.flush();
            return hub;
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HubErrorCode.HUB_NAME_DUPLICATED);
        }
    }

    // todo: 연관 허브 경로 비활성화
    // todo: 배송 담당자 논리 삭제 연동
    @Transactional
    public Hub deleteHub(UUID hubId, UUID userId) {

        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));

        hub.delete(userId);

        return hub;
    }
}
