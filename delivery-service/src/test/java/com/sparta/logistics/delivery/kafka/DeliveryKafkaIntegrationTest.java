package com.sparta.logistics.delivery.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.AiDeadlineCalculatedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.common.kafka.event.DeliveryStartedEvent;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.client.FeignCallService;
import com.sparta.logistics.delivery.client.response.HubRouteSegmentResponse;
import com.sparta.logistics.delivery.client.response.UserResponse;
import com.sparta.logistics.delivery.dto.event.StockReservedEventDto;
import com.sparta.logistics.delivery.dto.event.StockReservedItemPayload;
import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.DeliveryOrderItemEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import com.sparta.logistics.delivery.entity.enums.DeliveryStatus;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.outbox.OutboxEventPublisher;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import com.sparta.logistics.delivery.repository.DeliveryManagerRepository;
import com.sparta.logistics.delivery.repository.DeliveryOrderItemRepository;
import com.sparta.logistics.delivery.repository.DeliveryRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * delivery.created / delivery.started E2E Kafka 통합 테스트
 *
 * - EmbeddedKafka: 인메모리 브로커, 실제 Kafka 서버 불필요
 * - DirtiesContext: 테스트마다 컨텍스트 + H2 스키마 완전 초기화
 * - FeignCallService @MockitoBean: user-service / hub-service HTTP 호출 대체
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.STOCK_RESERVED,
                KafkaTopics.DELIVERY_CREATED,
                KafkaTopics.DELIVERY_CREATION_FAILED,
                KafkaTopics.AI_DEADLINE_CALCULATED,
                KafkaTopics.DELIVERY_STARTED
        }
)
class DeliveryKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryOrderItemRepository deliveryOrderItemRepository;

    @Autowired
    private DeliveryManagerRepository deliveryManagerRepository;

    @MockitoBean
    private FeignCallService feignCallService;

    @MockitoBean
    private OutboxEventPublisher outboxEventPublisher;

    private Consumer<String, String> deliveryCreatedConsumer;
    private Consumer<String, String> deliveryStartedConsumer;
    private Consumer<String, String> deliveryCreationFailedConsumer;

    private UUID orderId;
    private UUID receiverId;
    private UUID sourceHubId;
    private UUID destinationHubId;
    private UUID orderItemId;
    private UUID productId;
    private UUID managerId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        receiverId = UUID.randomUUID();
        sourceHubId = UUID.randomUUID();
        destinationHubId = UUID.randomUUID();
        orderItemId = UUID.randomUUID();
        productId = UUID.randomUUID();
        managerId = UUID.randomUUID();

        deliveryManagerRepository.save(
                new DeliveryManagerEntity(managerId, sourceHubId, "slack-manager", DeliveryManagerType.HUB_DELIVERY, 0)
        );

        Map<String, Object> createdProps = KafkaTestUtils.consumerProps("test-delivery-created", "true", embeddedKafkaBroker);
        deliveryCreatedConsumer = new DefaultKafkaConsumerFactory<>(
                createdProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(deliveryCreatedConsumer, KafkaTopics.DELIVERY_CREATED);

        Map<String, Object> startedProps = KafkaTestUtils.consumerProps("test-delivery-started", "true", embeddedKafkaBroker);
        deliveryStartedConsumer = new DefaultKafkaConsumerFactory<>(
                startedProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(deliveryStartedConsumer, KafkaTopics.DELIVERY_STARTED);

        Map<String, Object> failedProps = KafkaTestUtils.consumerProps("test-delivery-creation-failed", "true", embeddedKafkaBroker);
        deliveryCreationFailedConsumer = new DefaultKafkaConsumerFactory<>(
                failedProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(deliveryCreationFailedConsumer, KafkaTopics.DELIVERY_CREATION_FAILED);
    }

    @AfterEach
    void tearDown() {
        deliveryCreatedConsumer.close();
        deliveryStartedConsumer.close();
        deliveryCreationFailedConsumer.close();
    }

    @Test
    @DisplayName("stock.reserved 수신 시 DeliveryEntity 저장 및 delivery.created 발행")
    void stockReserved_success_deliveryCreatedPublished() throws Exception {
        // given
        when(feignCallService.fetchUser(receiverId))
                .thenReturn(ApiResponse.ok(new UserResponse(receiverId, "slack-receiver-42")));
        when(feignCallService.fetchRouteSegments(sourceHubId, destinationHubId))
                .thenReturn(List.of(
                        new HubRouteSegmentResponse(1, false, sourceHubId, destinationHubId, BigDecimal.valueOf(200), 90),
                        new HubRouteSegmentResponse(2, true, destinationHubId, null, BigDecimal.valueOf(30), 30)
                ));

        StockReservedEventDto event = new StockReservedEventDto(
                orderId, receiverId, sourceHubId, destinationHubId,
                "부산시 해운대구 배송지 123", "서울 허브", "부산 허브",
                List.of(new StockReservedItemPayload(orderItemId, productId, sourceHubId, 5)),
                1
        );

        // when
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVED, orderId.toString(), objectMapper.writeValueAsString(event)).get();

        // then - DB 검증
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            DeliveryEntity saved = deliveryRepository.findAll().stream()
                    .filter(d -> orderId.equals(d.getOrderId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("DeliveryEntity not found"));

            assertThat(saved.getStatus()).isEqualTo(DeliveryStatus.CREATED);
            assertThat(saved.getSourceHubId()).isEqualTo(sourceHubId);
            assertThat(saved.getDestinationHubId()).isEqualTo(destinationHubId);
            assertThat(saved.getReceiverSlackId()).isEqualTo("slack-receiver-42");
            assertThat(saved.getDeliveryAddress()).isEqualTo("부산시 해운대구 배송지 123");

            List<DeliveryOrderItemEntity> items = deliveryOrderItemRepository.findByDelivery_Id(saved.getId());
            assertThat(items).hasSize(1);
            assertThat(items.get(0).getProductId()).isEqualTo(productId);
            assertThat(items.get(0).getQuantity()).isEqualTo(5);
        });

        // then - Kafka 이벤트 검증
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                deliveryCreatedConsumer, KafkaTopics.DELIVERY_CREATED, Duration.ofSeconds(5));
        DeliveryCreatedEvent published = objectMapper.readValue(record.value(), DeliveryCreatedEvent.class);

        UUID deliveryId = deliveryRepository.findAll().stream()
                .filter(d -> orderId.equals(d.getOrderId()))
                .map(DeliveryEntity::getId)
                .findFirst().orElseThrow();

        assertThat(published.getEventId()).isNotNull();
        assertThat(published.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getSourceHubId()).isEqualTo(sourceHubId);
        assertThat(published.getDestinationHubId()).isEqualTo(destinationHubId);
        assertThat(published.getTotalDeliveryCount()).isEqualTo(1);
        assertThat(published.getDeliveryAddress()).isEqualTo("부산시 해운대구 배송지 123");
        assertThat(published.getTotalEstimatedDuration()).isEqualTo(120);
        assertThat(published.getReceiverSlackId()).isEqualTo("slack-receiver-42");
        assertThat(published.getSourceHubName()).isEqualTo("서울 허브");
        assertThat(published.getDestinationHubName()).isEqualTo("부산 허브");
        assertThat(published.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("ai.deadline.calculated 수신 시 deadline 업데이트 및 delivery.started 발행")
    void aiDeadlineCalculated_success_deliveryStartedPublished() throws Exception {
        // given - DeliveryEntity + OrderItem 직접 저장
        DeliveryEntity delivery = new DeliveryEntity(
                orderId, receiverId, sourceHubId, destinationHubId, "부산시 해운대구 배송지 123", "slack-42"
        );
        deliveryRepository.save(delivery);
        UUID deliveryId = delivery.getId();

        deliveryOrderItemRepository.save(
                new DeliveryOrderItemEntity(delivery, orderItemId, productId, sourceHubId, 5)
        );

        LocalDateTime deadline = LocalDateTime.now().plusHours(3);

        AiDeadlineCalculatedEvent event = AiDeadlineCalculatedEvent.builder()
                .eventId(UUID.randomUUID())
                .deliveryId(deliveryId)
                .orderId(orderId)
                .finalDispatchDeadlineAt(deadline)
                .build();

        // when
        kafkaTemplate.send(KafkaTopics.AI_DEADLINE_CALCULATED, deliveryId.toString(), objectMapper.writeValueAsString(event)).get();

        // then - DB 검증
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            DeliveryEntity updated = deliveryRepository.findById(deliveryId).orElseThrow();
            assertThat(updated.getFinalDispatchDeadlineAt()).isNotNull();
            assertThat(updated.getFinalDispatchDeadlineAt().truncatedTo(ChronoUnit.SECONDS))
                    .isEqualTo(deadline.truncatedTo(ChronoUnit.SECONDS));
        });

        // then - Kafka 이벤트 검증
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                deliveryStartedConsumer, KafkaTopics.DELIVERY_STARTED, Duration.ofSeconds(5));
        DeliveryStartedEvent published = objectMapper.readValue(record.value(), DeliveryStartedEvent.class);

        assertThat(published.getEventId()).isNotNull();
        assertThat(published.getDeliveryId()).isEqualTo(deliveryId);
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getOrderItems()).hasSize(1);
        assertThat(published.getOrderItems().get(0).getOrderItemId()).isEqualTo(orderItemId);
        assertThat(published.getOrderItems().get(0).getProductId()).isEqualTo(productId);
        assertThat(published.getOrderItems().get(0).getHubId()).isEqualTo(sourceHubId);
        assertThat(published.getOrderItems().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("중복 stock.reserved 수신 시 배송이 한 번만 생성된다 (멱등성)")
    void stockReserved_idempotency_duplicateIgnored() throws Exception {
        // given
        when(feignCallService.fetchUser(receiverId))
                .thenReturn(ApiResponse.ok(new UserResponse(receiverId, "slack-42")));
        when(feignCallService.fetchRouteSegments(sourceHubId, destinationHubId))
                .thenReturn(List.of(
                        new HubRouteSegmentResponse(1, true, sourceHubId, destinationHubId, BigDecimal.valueOf(100), 60)
                ));

        StockReservedEventDto event = new StockReservedEventDto(
                orderId, receiverId, sourceHubId, destinationHubId,
                "주소", "서울 허브", "부산 허브",
                List.of(new StockReservedItemPayload(orderItemId, productId, sourceHubId, 3)),
                1
        );
        String message = objectMapper.writeValueAsString(event);

        // when - 동일 메시지 2회 전송
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVED, orderId.toString(), message).get();
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVED, orderId.toString(), message).get();

        // then - DB에 1개만 생성
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = deliveryRepository.findAll().stream()
                    .filter(d -> orderId.equals(d.getOrderId()))
                    .count();
            assertThat(count).isEqualTo(1);
        });

        // then - delivery.created 1회만 발행
        KafkaTestUtils.getSingleRecord(deliveryCreatedConsumer, KafkaTopics.DELIVERY_CREATED, Duration.ofSeconds(5));
        var extra = deliveryCreatedConsumer.poll(Duration.ofSeconds(2));
        assertThat(extra.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("존재하지 않는 deliveryId로 ai.deadline.calculated 수신 시 delivery.started 미발행")
    void aiDeadlineCalculated_deliveryNotFound_noStartedPublished() throws Exception {
        // given - DB에 아무 배송도 없음
        UUID nonExistentDeliveryId = UUID.randomUUID();
        AiDeadlineCalculatedEvent event = AiDeadlineCalculatedEvent.builder()
                .eventId(UUID.randomUUID())
                .deliveryId(nonExistentDeliveryId)
                .orderId(UUID.randomUUID())
                .finalDispatchDeadlineAt(LocalDateTime.now().plusHours(1))
                .build();

        // when
        kafkaTemplate.send(KafkaTopics.AI_DEADLINE_CALCULATED, nonExistentDeliveryId.toString(),
                objectMapper.writeValueAsString(event)).get();

        // then - delivery.started 미발행 확인 (handler가 BusinessException 삼킴)
        await().during(3, TimeUnit.SECONDS).atMost(4, TimeUnit.SECONDS).untilAsserted(() -> {
            var records = deliveryStartedConsumer.poll(Duration.ofMillis(500));
            assertThat(records.count()).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("user-service 호출 실패 시 delivery.creation.failed 발행")
    void stockReserved_userServiceFails_creationFailedPublished() throws Exception {
        // given
        when(feignCallService.fetchUser(receiverId))
                .thenThrow(new BusinessException(DeliveryErrorCode.USER_SERVICE_UNAVAILABLE));

        StockReservedEventDto event = new StockReservedEventDto(
                orderId, receiverId, sourceHubId, destinationHubId,
                "주소", "서울 허브", "부산 허브",
                List.of(new StockReservedItemPayload(orderItemId, productId, sourceHubId, 2)),
                1
        );

        // when
        kafkaTemplate.send(KafkaTopics.STOCK_RESERVED, orderId.toString(), objectMapper.writeValueAsString(event)).get();

        // then
        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                deliveryCreationFailedConsumer, KafkaTopics.DELIVERY_CREATION_FAILED, Duration.ofSeconds(5));
        DeliveryCreationFailedEvent failed = objectMapper.readValue(record.value(), DeliveryCreationFailedEvent.class);

        assertThat(failed.getOrderId()).isEqualTo(orderId);
        assertThat(failed.getReason()).isEqualTo("USER_SERVICE_UNAVAILABLE");
        assertThat(failed.getItemsToRestore()).hasSize(1);
        assertThat(failed.getItemsToRestore().get(0).getProductId()).isEqualTo(productId);
        assertThat(failed.getItemsToRestore().get(0).getQuantity()).isEqualTo(2);
    }
}
