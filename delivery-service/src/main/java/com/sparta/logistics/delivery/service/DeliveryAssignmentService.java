package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import com.sparta.logistics.delivery.entity.enums.RouteType;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryManagerRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 배차 서비스 — 배송 담당자를 라운드 로빈 방식으로 배정한다.
 *
 * <p>현재는 {@code POST /api/v1/deliveries/{id}/assign} (수동 트리거)에서 호출.
 * 추후 {@code delivery.created} Kafka consumer를 추가해도 이 메서드는 변경 없이 재사용 가능.
 *
 * <p>배정 순서:
 * <ol>
 *   <li>HUB_TO_HUB 구간 → HUB_DELIVERY 담당자 (route.sourceHubId 기준 라운드 로빈)</li>
 *   <li>HUB_TO_COMPANY 구간 → COMPANY_DELIVERY 담당자 (delivery.destinationHubId 기준)</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAssignmentService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final DeliveryManagerRepository deliveryManagerRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeliveryPermissionChecker permissionChecker;

    /**
     * 배송에 연결된 모든 구간에 담당자를 배정한다.
     *
     * @param deliveryId 배정할 배송 ID
     * @param actorId    요청 주체 ID (권한 검사용)
     * @param role       요청 주체 역할
     * @param hubId      요청 주체 허브 ID (HUB_MANAGER 권한 검사용)
     */
    @Retry(name = "assignment", fallbackMethod = "recoverAssignManagers")
    @Transactional
    public void assignManagers(UUID deliveryId, UUID actorId, Role role, UUID hubId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        if (delivery.isDeleted()) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_DELETED);
        }

        // MASTER 또는 자기 허브 배송만 배차 가능 (배송 쓰기 권한과 동일)
        permissionChecker.checkDeliveryWritePermission(delivery, actorId, role, hubId);

        List<DeliveryRouteEntity> routes =
                deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId);

        for (DeliveryRouteEntity route : routes) {
            DeliveryManagerType managerType =
                    route.getRouteType() == RouteType.HUB_TO_HUB
                            ? DeliveryManagerType.HUB_DELIVERY
                            : DeliveryManagerType.COMPANY_DELIVERY;

            UUID searchHubId = route.getRouteType() == RouteType.HUB_TO_HUB
                    ? route.getSourceHubId()          // HUB 구간: 출발 허브 소속 담당자
                    : delivery.getDestinationHubId(); // COMPANY 구간: 목적지 허브 소속 담당자

            DeliveryManagerEntity manager = deliveryManagerRepository
                    .findNextAssignee(searchHubId, managerType, DeliveryManagerStatus.IDLE)
                    .orElseThrow(() -> {
                        log.warn("[배차] 가용 담당자 없음 — hubId={}, type={}", searchHubId, managerType);
                        return new BusinessException(DeliveryErrorCode.NO_AVAILABLE_MANAGER);
                    });

            manager.assign();
            route.assignManager(manager.getId());

            // COMPANY_DELIVERY 담당자는 Delivery 본체에도 기록
            if (managerType == DeliveryManagerType.COMPANY_DELIVERY) {
                delivery.assignCompanyDeliveryManager(manager.getId());
            }

            deliveryLogRepository.save(new DeliveryLogEntity(
                    deliveryId,
                    DeliveryEventType.MANAGER_ASSIGNED,
                    delivery.getStatus(),
                    "담당자 배정: " + managerType + " → " + manager.getId(),
                    null,
                    actorId
            ));

            log.info("[배차] 담당자 배정 완료 — deliveryId={}, routeId={}, managerId={}, type={}",
                    deliveryId, route.getId(), manager.getId(), managerType);
        }
    }

    public void recoverAssignManagers(UUID deliveryId, UUID actorId, Role role, UUID hubId,
                                       ObjectOptimisticLockingFailureException e) {
        log.error("[배차] 낙관적 락 재시도 초과 — deliveryId={}", deliveryId);
        throw new BusinessException(DeliveryErrorCode.ASSIGNMENT_CONFLICT);
    }

    // Kafka 트리거용 — 권한 체크 없이 라운드 로빈 배차, 담당자 없으면 null 허용
    @Retry(name = "assignment", fallbackMethod = "recoverAssignManagersForSystem")
    @Transactional
    public void assignManagersForSystem(UUID deliveryId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        List<DeliveryRouteEntity> routes =
                deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId);

        for (DeliveryRouteEntity route : routes) {
            DeliveryManagerType managerType =
                    route.getRouteType() == RouteType.HUB_TO_HUB
                            ? DeliveryManagerType.HUB_DELIVERY
                            : DeliveryManagerType.COMPANY_DELIVERY;

            UUID searchHubId = route.getRouteType() == RouteType.HUB_TO_HUB
                    ? route.getSourceHubId()
                    : delivery.getDestinationHubId();

            DeliveryManagerEntity manager = deliveryManagerRepository
                    .findNextAssignee(searchHubId, managerType, DeliveryManagerStatus.IDLE)
                    .orElse(null);

            if (manager == null) {
                log.warn("[배차] 가용 담당자 없음 — deliveryId={}, hubId={}, type={}", deliveryId, searchHubId, managerType);
                continue;
            }

            manager.assign();
            route.assignManager(manager.getId());

            if (managerType == DeliveryManagerType.COMPANY_DELIVERY) {
                delivery.assignCompanyDeliveryManager(manager.getId());
            }

            deliveryLogRepository.save(new DeliveryLogEntity(
                    deliveryId,
                    DeliveryEventType.MANAGER_ASSIGNED,
                    delivery.getStatus(),
                    "담당자 배정: " + managerType + " → " + manager.getId(),
                    null,
                    null
            ));

            log.info("[배차] 담당자 배정 완료 — deliveryId={}, managerId={}, type={}",
                    deliveryId, manager.getId(), managerType);
        }
    }

    public void recoverAssignManagersForSystem(UUID deliveryId,
                                               ObjectOptimisticLockingFailureException e) {
        log.error("[배차][시스템] 낙관적 락 재시도 초과 — deliveryId={}", deliveryId);
        // 미배차 상태 유지, 스케줄러가 재시도
    }
}
