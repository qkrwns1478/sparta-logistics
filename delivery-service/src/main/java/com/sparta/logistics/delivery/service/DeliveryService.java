package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.dto.DeliveryDetailResponse;
import lombok.extern.slf4j.Slf4j;
import com.sparta.logistics.delivery.dto.DeliveryListResponse;
import com.sparta.logistics.delivery.dto.DeliverySearchCond;
import com.sparta.logistics.delivery.dto.DeliveryStatusChangeRequest;
import com.sparta.logistics.delivery.dto.DeliveryUpdateRequest;
import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;
import com.sparta.logistics.delivery.entity.enums.RouteType;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.infrastructure.event.DeliveryEventPublisher;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryRouteRepository deliveryRouteRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeliveryPermissionChecker permissionChecker;
    private final DeliveryEventPublisher eventPublisher;

    // 배송 단건 조회
    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDelivery(UUID deliveryId, UUID userId, String role,
                                              UUID hubId, UUID companyId) {
        DeliveryEntity entity = findActiveOrThrow(deliveryId);
        permissionChecker.checkDeliveryReadPermission(entity, userId, role, hubId, companyId);
        return DeliveryDetailResponse.from(entity);
    }

    // 배송 목록 조회
    @Transactional(readOnly = true)
    public Page<DeliveryListResponse> getDeliveryList(UUID userId, String role, UUID hubId,
                                                       Pageable pageable, DeliverySearchCond cond) {
        switch (role) {
            case "HUB_MANAGER" -> cond.setAuthorizedHubId(hubId);
            case "DELIVERY_MANAGER" -> cond.setAuthorizedManagerId(userId);
            case "MASTER", "COMPANY_MANAGER" -> {}
            default -> {}
        }
        Page<DeliveryEntity> deliveryPage = deliveryRepository.findAllByCondition(cond, pageable);
        return deliveryPage.map(d -> DeliveryListResponse.from(d, null, null, null));
    }

    // 배송 수정
    @Transactional
    public DeliveryDetailResponse updateDelivery(UUID deliveryId, DeliveryUpdateRequest req,
                                                  UUID userId, String role, UUID hubId) {
        DeliveryEntity entity = findActiveOrThrow(deliveryId);
        permissionChecker.checkDeliveryWritePermission(entity, userId, role, hubId);
        entity.update(req);
        return DeliveryDetailResponse.from(entity);
    }

    // 배송 상태 변경 (로그 동기 저장 — 같은 트랜잭션)
    @Transactional
    public DeliveryDetailResponse changeStatus(UUID deliveryId, DeliveryStatusChangeRequest req,
                                                UUID userId, String role, UUID hubId) {
        DeliveryEntity entity = findActiveOrThrow(deliveryId);
        permissionChecker.checkDeliveryStatusChangePermission(entity, userId, role, hubId);

        if (!entity.getStatus().canTransitionTo(req.status())) {
            throw new BusinessException(DeliveryErrorCode.INVALID_STATUS_TRANSITION);
        }
        entity.changeStatus(req.status());

        deliveryLogRepository.save(new DeliveryLogEntity(
                deliveryId, DeliveryEventType.STATUS_CHANGED, req.status(), null, null, userId
        ));
        return DeliveryDetailResponse.from(entity);
    }

    // 배송 삭제 (soft delete)
    @Transactional
    public void deleteDelivery(UUID deliveryId, UUID userId, String role) {
        DeliveryEntity entity = findActiveOrThrow(deliveryId);
        permissionChecker.checkDeletePermission(role);
        entity.delete(userId);

        deliveryLogRepository.save(new DeliveryLogEntity(
                deliveryId, DeliveryEventType.CANCELLED, null, "배송 삭제", null, userId
        ));
    }

    // DeliveryEventHandler에서 Feign 호출 후 진입 — 트랜잭션 범위 최소화
    // DeliveryEntity + DeliveryRouteEntity[] 를 단일 트랜잭션으로 저장: 하나 실패 시 전체 롤백
    @Transactional
    public void createDelivery(StockReservedEventDto event, String slackId,
                               List<HubRouteSegmentResponse> routeSegments) {
        // 멱등성 보장: Kafka at-least-once 중복 소비 방어
        // orderId 단독 체크 시 같은 주문의 다른 허브 이벤트를 중복으로 차단하므로 orderId+sourceHubId 조합 사용
        if (deliveryRepository.existsByOrderIdAndSourceHubId(event.orderId(), event.sourceHubId())) {
            log.info("[createDelivery] 이미 처리된 주문+허브 조합 — orderId={}, sourceHubId={}", event.orderId(), event.sourceHubId());
            return;
        }

        DeliveryEntity entity = new DeliveryEntity(
                event.orderId(),
                event.receiverId(),
                event.sourceHubId(),
                event.destinationHubId(),
                event.deliveryAddress(),
                slackId
        );
        deliveryRepository.save(entity);

        // 생성 로그 — actorId는 시스템 생성이므로 null
        deliveryLogRepository.save(new DeliveryLogEntity(
                entity.getId(),
                DeliveryEventType.CREATED,
                entity.getStatus(),
                "orderId=" + event.orderId() + ", sourceHubId=" + event.sourceHubId(),
                null,
                null
        ));

        // hub-service 구간 정보로 DeliveryRoute 일괄 저장
        for (HubRouteSegmentResponse seg : routeSegments) {
            RouteType routeType = seg.lastMile() ? RouteType.HUB_TO_COMPANY : RouteType.HUB_TO_HUB;
            deliveryRouteRepository.save(new DeliveryRouteEntity(
                    entity,
                    seg.sequence(),
                    routeType,
                    seg.sourceHubId(),
                    seg.destinationHubId(),
                    seg.estimatedDistance(),
                    seg.estimatedDuration()
            ));
        }

        // 트랜잭션 커밋 후 발행이 이상적이나 소규모 프로젝트 기준 단순 구조 채택
        // 추후 outbox 패턴으로 전환 시 이 호출 제거
        eventPublisher.publishCreated(entity.getId(), event.orderId());
    }

    // ai.deadline.calculated 이벤트 수신 시 호출
    @Transactional
    public void updateFinalDispatchDeadline(UUID deliveryId, LocalDateTime deadline) {
        DeliveryEntity entity = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        entity.updateFinalDispatchDeadline(deadline);
    }

    private DeliveryEntity findActiveOrThrow(UUID deliveryId) {
        DeliveryEntity entity = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        if (entity.isDeleted()) {
            throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_DELETED);
        }
        return entity;
    }
}
