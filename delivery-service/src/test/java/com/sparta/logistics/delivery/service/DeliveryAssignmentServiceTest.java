package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import com.sparta.logistics.delivery.entity.enums.DeliveryStatus;
import com.sparta.logistics.delivery.entity.enums.RouteType;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryManagerRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryAssignmentServiceTest {

    @Mock DeliveryRepository deliveryRepository;
    @Mock DeliveryRouteRepository deliveryRouteRepository;
    @Mock DeliveryManagerRepository deliveryManagerRepository;
    @Mock DeliveryLogRepository deliveryLogRepository;
    @Mock DeliveryPermissionChecker permissionChecker;

    @InjectMocks DeliveryAssignmentService service;

    private DeliveryEntity delivery() {
        return new DeliveryEntity(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "주소", "slack");
    }

    private DeliveryRouteEntity route(DeliveryEntity d, RouteType type) {
        return new DeliveryRouteEntity(d, 0, type,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 60);
    }

    // ── Fix ③: COMPLETED/CANCELLED 배송 배차 방어 ─────────────────────────

    @Test
    void COMPLETED_배송_수동배차_예외() {
        UUID deliveryId = UUID.randomUUID();
        DeliveryEntity d = delivery();
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        d.changeStatus(DeliveryStatus.DESTINATION_HUB_ARRIVED);
        d.changeStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        d.changeStatus(DeliveryStatus.COMPLETED);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.doAssignManagers(deliveryId, UUID.randomUUID(), Role.MASTER, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DeliveryErrorCode.DELIVERY_ROUTE_UPDATE_FORBIDDEN);
    }

    @Test
    void CANCELLED_배송_수동배차_예외() {
        UUID deliveryId = UUID.randomUUID();
        DeliveryEntity d = delivery();
        d.changeStatus(DeliveryStatus.CANCELLED);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.doAssignManagers(deliveryId, UUID.randomUUID(), Role.MASTER, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DeliveryErrorCode.DELIVERY_ROUTE_UPDATE_FORBIDDEN);
    }

    @Test
    void COMPLETED_배송_시스템배차_조용히_스킵() {
        UUID deliveryId = UUID.randomUUID();
        DeliveryEntity d = delivery();
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        d.changeStatus(DeliveryStatus.DESTINATION_HUB_ARRIVED);
        d.changeStatus(DeliveryStatus.OUT_FOR_DELIVERY);
        d.changeStatus(DeliveryStatus.COMPLETED);
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));

        service.doAssignManagersForSystem(deliveryId);

        verify(deliveryRouteRepository, never()).findAllByDelivery_IdOrderBySequenceAsc(any());
    }

    // ── Fix ①: 이미 배정된 route 재배정 방어 ────────────────────────────────

    @Test
    void 이미_배정된_route는_시스템배차에서_스킵() {
        UUID deliveryId = UUID.randomUUID();
        DeliveryEntity d = delivery();
        DeliveryRouteEntity assigned = route(d, RouteType.HUB_TO_HUB);
        UUID existingManagerId = UUID.randomUUID();
        assigned.assignManager(existingManagerId);

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));
        when(deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId))
                .thenReturn(List.of(assigned));

        service.doAssignManagersForSystem(deliveryId);

        // 이미 배정된 route라 새 manager 조회 안 함
        verify(deliveryManagerRepository, never()).findNextAssignee(any(), any(), any());
        assertThat(assigned.getHubDeliveryManagerId()).isEqualTo(existingManagerId);
    }

    @Test
    void 미배정_route만_배정됨() {
        UUID deliveryId = UUID.randomUUID();
        DeliveryEntity d = delivery();
        DeliveryRouteEntity assigned = route(d, RouteType.HUB_TO_HUB);
        assigned.assignManager(UUID.randomUUID()); // 이미 배정됨
        DeliveryRouteEntity unassigned = route(d, RouteType.HUB_TO_HUB);

        UUID managerId = UUID.randomUUID();
        DeliveryManagerEntity manager = new DeliveryManagerEntity(managerId,
                unassigned.getSourceHubId(), "slack", DeliveryManagerType.HUB_DELIVERY, 0);

        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));
        when(deliveryRouteRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId))
                .thenReturn(List.of(assigned, unassigned));
        when(deliveryManagerRepository.findNextAssignee(any(), any(), any()))
                .thenReturn(Optional.of(manager));
        when(deliveryLogRepository.save(any())).thenReturn(null);

        service.doAssignManagersForSystem(deliveryId);

        assertThat(manager.getStatus()).isEqualTo(DeliveryManagerStatus.WORKING);
        assertThat(unassigned.getHubDeliveryManagerId()).isEqualTo(managerId);
    }
}
