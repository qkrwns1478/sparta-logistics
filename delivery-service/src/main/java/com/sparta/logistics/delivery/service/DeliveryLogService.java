package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.dto.log.DeliveryLogResponse;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryLogService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryLogRepository logRepository;
    private final DeliveryPermissionChecker permissionChecker;

    // 배송 이벤트 로그 조회 — 배송 READ 권한과 동일
    @Transactional(readOnly = true)
    public List<DeliveryLogResponse> getLogs(UUID deliveryId, UUID userId, Role role,
                                              UUID hubId, UUID companyId) {
        DeliveryEntity delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(DeliveryErrorCode.DELIVERY_NOT_FOUND));
        if (delivery.isDeleted()) throw new BusinessException(DeliveryErrorCode.DELIVERY_ALREADY_DELETED);

        permissionChecker.checkDeliveryReadPermission(delivery, userId, role, hubId, companyId);

        return logRepository.findAllByDeliveryIdOrderByRecordedAtAsc(deliveryId)
                .stream()
                .map(DeliveryLogResponse::from)
                .toList();
    }
}
