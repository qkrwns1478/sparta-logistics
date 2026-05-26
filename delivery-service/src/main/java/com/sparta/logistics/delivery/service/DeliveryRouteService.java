package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteResponse;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteUpdateRequest;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;
import com.sparta.logistics.delivery.entity.enums.RouteStatus;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryRouteService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository routeRepository;
    private final DeliveryLogRepository logRepository;
    private final DeliveryPermissionChecker permissionChecker;

    // 배송경로 목록 조회
    @Transactional(readOnly = true)
    public List<DeliveryRouteResponse> getRouteList(UUID deliveryId, UUID userId, Role role,
                                                     UUID hubId, UUID companyId) {
        DeliveryEntity delivery = findDeliveryOrThrow(deliveryId);
        permissionChecker.checkDeliveryReadPermission(delivery, userId, role, hubId, companyId);

        return routeRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId)
                .stream()
                .map(DeliveryRouteResponse::from)
                .toList();
    }

    // 배송경로 수정 (구간 ARRIVED 시 로그 동기 저장)
    @Transactional
    public DeliveryRouteResponse updateRoute(UUID deliveryId, UUID routeId,
                                              DeliveryRouteUpdateRequest req,
                                              UUID userId, Role role, UUID hubId) {
        DeliveryEntity delivery = findDeliveryOrThrow(deliveryId);
        DeliveryRouteEntity route = routeRepository.findById(routeId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.ROUTE_NOT_FOUND));

        if (!route.getDelivery().getId().equals(deliveryId)) {
            throw new BusinessException(DeliveryErrorCode.ROUTE_NOT_FOUND);
        }

        permissionChecker.checkRouteWritePermission(delivery, route, userId, role, hubId);

        if (req.actualDistance() != null) route.updateActual(req.actualDistance(), req.actualDuration());
        // 멱등성 보장: 동일 상태 재요청 시 changeStatus 및 로그 INSERT 생략
        if (req.status() != null && route.getStatus() != req.status()) {
            route.changeStatus(req.status());
            if (req.status() == RouteStatus.ARRIVED) {
                logRepository.save(new DeliveryLogEntity(
                        deliveryId, DeliveryEventType.ROUTE_UPDATED, null,
                        sequence(route) + "번 구간 도착", null, userId
                ));
            }
        }

        return DeliveryRouteResponse.from(route);
    }

    private DeliveryEntity findDeliveryOrThrow(UUID deliveryId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        if (delivery.isDeleted()) throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_DELETED);
        return delivery;
    }

    private String sequence(DeliveryRouteEntity route) {
        return String.valueOf(route.getSequence() + 1);
    }
}
