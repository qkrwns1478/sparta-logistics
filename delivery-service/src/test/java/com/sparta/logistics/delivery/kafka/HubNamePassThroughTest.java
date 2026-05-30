package com.sparta.logistics.delivery.kafka;

import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.dto.event.StockReservedItemPayload;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.kafka.producer.DeliveryEventPublisher;
import com.sparta.logistics.delivery.repository.DeliveryLogRepository;
import com.sparta.logistics.delivery.repository.DeliveryOrderItemRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import com.sparta.logistics.delivery.service.DeliveryAssignmentService;
import com.sparta.logistics.delivery.service.DeliveryPermissionChecker;
import com.sparta.logistics.delivery.service.DeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sourceHubName / destinationHubName pass-through 검증
 *
 * StockReservedEventDto → DeliveryService.createDelivery → DeliveryEventPublisher.publishCreated
 * 경로에서 허브 이름이 DB 저장 없이 이벤트 페이로드로 전달되는지 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class HubNamePassThroughTest {

    @Mock DeliveryRepository deliveryRepository;
    @Mock DeliveryRouteRepository deliveryRouteRepository;
    @Mock DeliveryOrderItemRepository deliveryOrderItemRepository;
    @Mock DeliveryLogRepository deliveryLogRepository;
    @Mock DeliveryPermissionChecker permissionChecker;
    @Mock DeliveryEventPublisher eventPublisher;
    @Mock DeliveryAssignmentService assignmentService;

    @InjectMocks DeliveryService deliveryService;

    @BeforeEach
    void setUpTransaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @Test
    @DisplayName("StockReservedEventDto의 허브 이름이 publishCreated 호출 시 그대로 전달된다")
    void hubNameIsPassedThroughToPublishCreated() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID sourceHubId = UUID.randomUUID();
        UUID destinationHubId = UUID.randomUUID();
        String sourceHubName = "서울 허브";
        String destinationHubName = "부산 허브";

        StockReservedEventDto event = new StockReservedEventDto(
                orderId,
                UUID.randomUUID(),
                sourceHubId,
                destinationHubId,
                "부산시 해운대구 123",
                sourceHubName,
                destinationHubName,
                List.of(new StockReservedItemPayload(UUID.randomUUID(), UUID.randomUUID(), sourceHubId, 2)),
                1
        );

        HubRouteSegmentResponse segment = new HubRouteSegmentResponse(
                1, true, sourceHubId, destinationHubId,
                BigDecimal.valueOf(100), 120
        );

        when(deliveryRepository.existsByOrderIdAndSourceHubId(orderId, sourceHubId)).thenReturn(false);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRouteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        deliveryService.createDelivery(event, "slack-id-123", List.of(segment));

        // afterCommit 동기화 수동 실행
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCommit());

        // then
        ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);

        verify(eventPublisher).publishCreated(
                any(), any(), any(), any(), any(), any(int.class),
                any(), any(int.class),
                any(), sourceCaptor.capture(), destCaptor.capture(), any()
        );

        assertThat(sourceCaptor.getValue()).isEqualTo("서울 허브");
        assertThat(destCaptor.getValue()).isEqualTo("부산 허브");
    }

    @Test
    @DisplayName("허브 이름이 null이어도 NPE 없이 publishCreated가 호출된다")
    void hubNameNullDoesNotThrow() {
        // given
        UUID orderId = UUID.randomUUID();
        UUID sourceHubId = UUID.randomUUID();
        UUID destinationHubId = UUID.randomUUID();

        StockReservedEventDto event = new StockReservedEventDto(
                orderId,
                UUID.randomUUID(),
                sourceHubId,
                destinationHubId,
                "주소",
                null,   // sourceHubName null
                null,   // destinationHubName null
                List.of(new StockReservedItemPayload(UUID.randomUUID(), UUID.randomUUID(), sourceHubId, 1)),
                1
        );

        HubRouteSegmentResponse segment = new HubRouteSegmentResponse(
                1, true, sourceHubId, destinationHubId,
                BigDecimal.valueOf(50), 60
        );

        when(deliveryRepository.existsByOrderIdAndSourceHubId(orderId, sourceHubId)).thenReturn(false);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRouteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryOrderItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when & then — 예외 없이 실행
        deliveryService.createDelivery(event, "slack-id", List.of(segment));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCommit());

        verify(eventPublisher).publishCreated(
                any(), any(), any(), any(), any(), any(int.class),
                any(), any(int.class),
                any(), any(), any(), any()
        );
    }

    @org.junit.jupiter.api.AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }
}
