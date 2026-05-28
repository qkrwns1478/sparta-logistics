package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteUpdateRequest;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import com.sparta.logistics.delivery.entity.enums.DeliveryStatus;
import com.sparta.logistics.delivery.entity.enums.RouteStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

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
class DeliveryRouteServiceTest {

    @Mock DeliveryRepository deliveryRepository;
    @Mock DeliveryRouteRepository routeRepository;
    @Mock DeliveryLogRepository logRepository;
    @Mock DeliveryManagerRepository managerRepository;
    @Mock DeliveryPermissionChecker permissionChecker;

    @InjectMocks DeliveryRouteService service;

    private DeliveryEntity delivery(UUID id) {
        DeliveryEntity d = new DeliveryEntity(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "주소", "slack");
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private DeliveryRouteEntity route(DeliveryEntity d, RouteType type) {
        return new DeliveryRouteEntity(d, 0, type,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 60);
    }

    private void stub(UUID deliveryId, DeliveryEntity d, UUID routeId, DeliveryRouteEntity r) {
        when(deliveryRepository.findById(deliveryId)).thenReturn(Optional.of(d));
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(r));
        doNothing().when(permissionChecker).checkRouteWritePermission(any(), any(), any(), any(), any());
    }

    // ── Fix ④: ROUTE_SEQUENCE_VIOLATED ──────────────────────────────────────

    @Test
    void HUB_TO_COMPANY_IN_TRANSIT_목적지_미도착시_예외() {
        UUID deliveryId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        DeliveryEntity d = delivery(deliveryId);
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING); // DESTINATION_HUB_ARRIVED 아님
        DeliveryRouteEntity r = route(d, RouteType.HUB_TO_COMPANY);
        stub(deliveryId, d, routeId, r);

        assertThatThrownBy(() -> service.updateRoute(deliveryId, routeId,
                new DeliveryRouteUpdateRequest(RouteStatus.IN_TRANSIT, null, null),
                UUID.randomUUID(), Role.MASTER, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DeliveryErrorCode.ROUTE_SEQUENCE_VIOLATED));
    }

    @Test
    void HUB_TO_COMPANY_IN_TRANSIT_목적지_도착후_정상_전환() {
        UUID deliveryId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        DeliveryEntity d = delivery(deliveryId);
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        d.changeStatus(DeliveryStatus.DESTINATION_HUB_ARRIVED);
        DeliveryRouteEntity r = route(d, RouteType.HUB_TO_COMPANY);
        stub(deliveryId, d, routeId, r);

        service.updateRoute(deliveryId, routeId,
                new DeliveryRouteUpdateRequest(RouteStatus.IN_TRANSIT, null, null),
                UUID.randomUUID(), Role.MASTER, null);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.OUT_FOR_DELIVERY);
    }

    // ── Fix ⑤: isNextRouteLastMile 다음 구간 없을 때 DESTINATION_HUB_ARRIVED 전이 ──

    @Test
    void HUB_TO_HUB_ARRIVED_다음_구간_없으면_DESTINATION_HUB_ARRIVED() {
        UUID deliveryId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        DeliveryEntity d = delivery(deliveryId);
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        DeliveryRouteEntity r = route(d, RouteType.HUB_TO_HUB);
        r.changeStatus(RouteStatus.IN_TRANSIT);
        stub(deliveryId, d, routeId, r);
        when(logRepository.save(any())).thenReturn(null);
        when(routeRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId)).thenReturn(List.of(r));

        service.updateRoute(deliveryId, routeId,
                new DeliveryRouteUpdateRequest(RouteStatus.ARRIVED, null, null),
                UUID.randomUUID(), Role.MASTER, null);

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.DESTINATION_HUB_ARRIVED);
    }

    // ── 담당자 IDLE 복귀 ────────────────────────────────────────────────────

    @Test
    void ARRIVED시_담당자_IDLE로_복귀() {
        UUID deliveryId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        DeliveryEntity d = delivery(deliveryId);
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        DeliveryRouteEntity r = route(d, RouteType.HUB_TO_HUB);
        r.changeStatus(RouteStatus.IN_TRANSIT);
        r.assignManager(managerId);
        stub(deliveryId, d, routeId, r);
        when(logRepository.save(any())).thenReturn(null);
        when(routeRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId)).thenReturn(List.of(r));

        DeliveryManagerEntity manager = new DeliveryManagerEntity(managerId, UUID.randomUUID(),
                "slack", DeliveryManagerType.HUB_DELIVERY, 0);
        manager.assign();
        when(managerRepository.findById(managerId)).thenReturn(Optional.of(manager));

        service.updateRoute(deliveryId, routeId,
                new DeliveryRouteUpdateRequest(RouteStatus.ARRIVED, null, null),
                UUID.randomUUID(), Role.MASTER, null);

        assertThat(manager.getStatus()).isEqualTo(DeliveryManagerStatus.IDLE);
    }

    @Test
    void ARRIVED시_담당자_없으면_예외_없이_통과() {
        UUID deliveryId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        DeliveryEntity d = delivery(deliveryId);
        d.changeStatus(DeliveryStatus.HUB_WAITING);
        d.changeStatus(DeliveryStatus.HUB_MOVING);
        DeliveryRouteEntity r = route(d, RouteType.HUB_TO_HUB);
        r.changeStatus(RouteStatus.IN_TRANSIT);
        // managerId = null
        stub(deliveryId, d, routeId, r);
        when(logRepository.save(any())).thenReturn(null);
        when(routeRepository.findAllByDelivery_IdOrderBySequenceAsc(deliveryId)).thenReturn(List.of(r));

        service.updateRoute(deliveryId, routeId,
                new DeliveryRouteUpdateRequest(RouteStatus.ARRIVED, null, null),
                UUID.randomUUID(), Role.MASTER, null);

        verify(managerRepository, never()).findById(any());
    }
}
