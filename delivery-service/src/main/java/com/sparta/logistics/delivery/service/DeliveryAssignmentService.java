package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.DeliveryStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
 *
 * <p>{@code @Retry}와 {@code @Transactional}을 같은 메서드에 선언하면 AOP 프록시 순서가
 * 보장되지 않아 {@code UnexpectedRollbackException}이 발생할 수 있다.
 * 이를 방지하기 위해 재시도 담당 메서드와 트랜잭션 담당 메서드를 분리하고,
 * self-injection({@code @Lazy @Autowired})으로 프록시를 통해 호출한다.
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

    // 재시도마다 @Transactional 프록시를 새로 통과시키기 위해 self-injection 사용
    // @Lazy: 빈 생성 시점의 순환 의존성을 피해 첫 호출 시점에 주입
    @Lazy
    @Autowired
    private DeliveryAssignmentService self;

    /**
     * 배송에 연결된 모든 구간에 담당자를 배정한다. (재시도 담당)
     *
     * @param deliveryId 배정할 배송 ID
     * @param actorId    요청 주체 ID (권한 검사용)
     * @param role       요청 주체 역할
     * @param hubId      요청 주체 허브 ID (HUB_MANAGER 권한 검사용)
     */
    @Retry(name = "assignment", fallbackMethod = "recoverAssignManagers")
    public void assignManagers(UUID deliveryId, UUID actorId, Role role, UUID hubId) {
        self.doAssignManagers(deliveryId, actorId, role, hubId);
    }

    /** 실제 배차 DB 작업 (트랜잭션 담당) — 재시도마다 새 트랜잭션으로 실행된다. */
    @Transactional
    public void doAssignManagers(UUID deliveryId, UUID actorId, Role role, UUID hubId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        if (delivery.isDeleted()) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_DELETED);
        }

        if (delivery.getStatus() == DeliveryStatus.COMPLETED
                || delivery.getStatus() == DeliveryStatus.CANCELLED) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ROUTE_UPDATE_FORBIDDEN);
        }

        // MASTER 또는 자기 허브 배송만 배차 가능 (배송 쓰기 권한과 동일)
        permissionChecker.checkDeliveryWritePermission(delivery, actorId, role, hubId);

        List<DeliveryRouteEntity> routes =
                deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId);

        for (DeliveryRouteEntity route : routes) {
            if (route.getHubDeliveryManagerId() != null) continue;
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

    /** 시스템 배차 진입점 (재시도 담당) */
    @Retry(name = "assignment", fallbackMethod = "recoverAssignManagersForSystem")
    public void assignManagersForSystem(UUID deliveryId) {
        self.doAssignManagersForSystem(deliveryId);
    }

    /** 시스템 배차 DB 작업 (트랜잭션 담당) — 재시도마다 새 트랜잭션으로 실행된다. */
    @Transactional
    public void doAssignManagersForSystem(UUID deliveryId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));

        if (delivery.getStatus() == DeliveryStatus.COMPLETED
                || delivery.getStatus() == DeliveryStatus.CANCELLED) {
            log.warn("[배차][시스템] 종료된 배송 배차 시도 무시 — deliveryId={}, status={}", deliveryId, delivery.getStatus());
            return;
        }

        List<DeliveryRouteEntity> routes =
                deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId);

        for (DeliveryRouteEntity route : routes) {
            if (route.getHubDeliveryManagerId() != null) continue;
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
