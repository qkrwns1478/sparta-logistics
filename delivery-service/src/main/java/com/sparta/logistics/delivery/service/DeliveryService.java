package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.dto.DeliveryDetailResponse;
import lombok.extern.slf4j.Slf4j;
import com.sparta.logistics.delivery.dto.DeliveryListResponse;
import com.sparta.logistics.delivery.dto.DeliverySearchCond;
import com.sparta.logistics.delivery.dto.DeliveryStatusChangeRequest;
import com.sparta.logistics.delivery.dto.DeliveryUpdateRequest;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeliveryPermissionChecker permissionChecker;

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
    @Transactional
    public void createDelivery(StockReservedEventDto event, String slackId) {
        // 멱등성 보장: Kafka at-least-once 중복 소비 방어
        // orderId UNIQUE 제약이 최종 방어선이지만, 명시적 체크로 DataIntegrityViolationException 사전 차단
        if (deliveryRepository.existsByOrderId(event.orderId())) {
            log.info("[createDelivery] 이미 처리된 주문 — orderId={}", event.orderId());
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
