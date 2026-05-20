package com.sparta.logistics.hub.hubroute.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubErrorCode;
import com.sparta.logistics.hub.exception.HubRouteErrorCode;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import com.sparta.logistics.hub.hubroute.dto.request.CreateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.response.HubRouteCreateResponse;
import com.sparta.logistics.hub.hubroute.entity.HubRoute;
import com.sparta.logistics.hub.hubroute.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubRouteService {

    private final HubRouteRepository hubRouteRepository;
    private final HubRepository hubRepository;

    @Transactional
    public HubRouteCreateResponse createHubRoute(CreateHubRouteRequest request) {

        if (request.getSourceHubId().equals(request.getDestinationHubId())) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_SAME_HUB);
        }

        Hub sourceHub = findHubById(request.getSourceHubId());
        Hub destinationHub = findHubById(request.getDestinationHubId());

        if (hubRouteRepository
                .existsBySourceHubAndDestinationHubAndDeletedAtIsNull(sourceHub, destinationHub)) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_ALREADY_EXISTS);
        }

        HubRoute hubRoute = HubRoute.create(
                sourceHub,
                destinationHub,
                request.getDistance(),
                request.getDuration()
        );

        try {
            HubRoute savedRoute = hubRouteRepository.save(hubRoute);
            hubRouteRepository.flush();
            return HubRouteCreateResponse.from(savedRoute);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_ALREADY_EXISTS);
        }
    }

    private Hub findHubById(UUID hubId) {

        return hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }
}
