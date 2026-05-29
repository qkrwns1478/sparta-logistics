package com.sparta.logistics.delivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.DeliveryOrderItemPayload;
import com.sparta.logistics.common.kafka.event.DeliveryStartedEvent;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryOrderItemEntity;
import com.sparta.logistics.delivery.kafka.producer.DeliveryEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * orderItemId 파이프라인 검증:
 *   stock.reserved → DeliveryOrderItemEntity → delivery.started 페이로드
 *
 * Task 2 구현 완료 후 통과해야 함.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryOrderItemIdPipelineTest {

    // ── 1. DeliveryOrderItemEntity 생성 검증 ─────────────────────────────────

    @Test
    void DeliveryOrderItemEntity_생성시_orderItemId_보존() {
        UUID orderItemId = UUID.randomUUID();
        UUID productId   = UUID.randomUUID();
        UUID hubId       = UUID.randomUUID();
        DeliveryEntity delivery = new DeliveryEntity(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                "서울시 강남구", "slack123"
        );

        DeliveryOrderItemEntity entity = new DeliveryOrderItemEntity(
                delivery, orderItemId, productId, hubId, 5
        );

        assertThat(entity.getOrderItemId()).isEqualTo(orderItemId);
        assertThat(entity.getProductId()).isEqualTo(productId);
        assertThat(entity.getHubId()).isEqualTo(hubId);
        assertThat(entity.getQuantity()).isEqualTo(5);
    }

    @Test
    void DeliveryOrderItemEntity_orderItemId_null_허용() {
        // stock.reserved에 orderItemId 없는 레거시 이벤트 대비 null 허용
        DeliveryEntity delivery = new DeliveryEntity(
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                "서울시 강남구", "slack123"
        );

        DeliveryOrderItemEntity entity = new DeliveryOrderItemEntity(
                delivery, null, UUID.randomUUID(), UUID.randomUUID(), 3
        );

        assertThat(entity.getOrderItemId()).isNull();
    }

    // ── 2. publishStarted() Kafka 페이로드 검증 ───────────────────────────────

    @Mock KafkaTemplate<String, String> kafkaTemplate;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks DeliveryEventPublisher publisher;

    @Test
    void publishStarted_orderItemId는_entity_PK가_아닌_주문항목ID() throws Exception {
        UUID deliveryId  = UUID.randomUUID();
        UUID orderId     = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID(); // 원래 주문 항목 ID
        UUID entityPk    = UUID.randomUUID(); // delivery-service 내부 PK (달라야 함)

        DeliveryEntity delivery = new DeliveryEntity(
                orderId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                "주소", "slack"
        );
        DeliveryOrderItemEntity item = new DeliveryOrderItemEntity(
                delivery, orderItemId, UUID.randomUUID(), UUID.randomUUID(), 2
        );
        ReflectionTestUtils.setField(item, "id", entityPk); // entity PK를 orderItemId와 다른 값으로 주입

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        publisher.publishStarted(deliveryId, orderId, List.of(item));

        verify(kafkaTemplate).send(anyString(), anyString(), msgCaptor.capture());

        DeliveryStartedEvent event = objectMapper.readValue(
                msgCaptor.getValue(), DeliveryStartedEvent.class
        );
        DeliveryOrderItemPayload payload = event.getOrderItems().get(0);

        assertThat(payload.getOrderItemId())
                .as("orderItemId는 원래 주문 항목 ID여야 한다 (entity PK 아님)")
                .isEqualTo(orderItemId)
                .isNotEqualTo(entityPk);
    }
}
