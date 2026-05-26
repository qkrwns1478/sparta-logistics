package com.sparta.logistics.hub.hubroute.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.hub.exception.HubErrorCode;
import com.sparta.logistics.hub.exception.HubRouteErrorCode;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import com.sparta.logistics.hub.hubroute.dto.request.CreateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.request.UpdateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.response.*;
import com.sparta.logistics.hub.hubroute.entity.HubRoute;
import com.sparta.logistics.hub.hubroute.repository.HubRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HubRouteService {

    private final HubRouteRepository hubRouteRepository;
    private final HubRepository hubRepository;


    @Transactional
    public HubRouteDetailResponse createHubRoute(CreateHubRouteRequest request, Role role) {

        // master 검증
        if (!isMaster(role)) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_FORBIDDEN);
        }

        if (request.getSourceHubId().equals(request.getDestinationHubId())) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_SAME_HUB);
        }

        Hub sourceHub = findHubById(request.getSourceHubId());
        Hub destinationHub = findHubById(request.getDestinationHubId());

        boolean exists = hubRouteRepository
                .existsBySourceHubAndDestinationHubAndDeletedAtIsNull(sourceHub, destinationHub);

        if (exists) {
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
            return HubRouteDetailResponse.from(savedRoute);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uk_hub_route_source_destination")) {
                throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    @Cacheable(
            value = "hubRouteList",
            key = "#sourceHubId +" +
                    " ':' + #destinationHubId +" +
                    " ':' + #pageable.pageNumber +" +
                    " ':' + #pageable.pageSize"
    )
    @Transactional(readOnly = true)
    public Page<HubRouteListResponse> getHubRouteList(UUID sourceHubId, UUID destinationHubId, Pageable pageable) {

        return hubRouteRepository.findAllByCondition(sourceHubId, destinationHubId, pageable)
                .map(HubRouteListResponse::from);
    }

    @Cacheable(value = "hubRoutes", key = "#routeId")
    @Transactional(readOnly = true)
    public HubRouteDetailResponse getHubRoute(UUID routeId) {

        HubRoute hubRoute = hubRouteRepository.findByIdWithHubs(routeId)
                .orElseThrow(() -> new BusinessException(HubRouteErrorCode.HUB_ROUTE_NOT_FOUND));

        return HubRouteDetailResponse.from(hubRoute);
    }

    @Caching(evict = {
            @CacheEvict(value = "hubRoutes", key = "#routeId"),
            @CacheEvict(value = "hubRouteList", allEntries = true)
    })
    @Transactional
    public HubRouteUpdateResponse updateHubRoute(UUID routeId, UpdateHubRouteRequest request, Role role) {

        // master 검증
        if (!isMaster(role)) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_FORBIDDEN);
        }

        HubRoute hubRoute = findHubRouteById(routeId);

        hubRoute.update(request.getDistance(), request.getDuration());

        return HubRouteUpdateResponse.from(hubRoute);
    }

    @Caching(evict = {
            @CacheEvict(value = "hubRoutes", key = "#routeId"),
            @CacheEvict(value = "hubRouteList", allEntries = true)
    })
    @Transactional
    public HubRouteDeleteResponse deleteHubRoute(UUID routeId, UUID userId, Role role) {

        // master 검증
        if (!isMaster(role)) {
            throw new BusinessException(HubRouteErrorCode.HUB_ROUTE_FORBIDDEN);
        }

        HubRoute hubRoute = findHubRouteById(routeId);

        hubRoute.delete(userId);

        return HubRouteDeleteResponse.from(hubRoute);
    }

    private Hub findHubById(UUID hubId) {

        return hubRepository.findByIdAndDeletedAtIsNull(hubId)
                .orElseThrow(() -> new BusinessException(HubErrorCode.HUB_NOT_FOUND));
    }

    private HubRoute findHubRouteById(UUID hubRouteId) {

        return hubRouteRepository.findByIdAndDeletedAtIsNull(hubRouteId)
                .orElseThrow(() -> new BusinessException(HubRouteErrorCode.HUB_ROUTE_NOT_FOUND));
    }

    private boolean isMaster(Role role) {
        return role.equals(Role.MASTER);
    }
}
