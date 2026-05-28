package com.sparta.logistics.hub.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.*;
import com.sparta.logistics.hub.client.CompanyClient;
import com.sparta.logistics.hub.client.response.CompanyResponse;
import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.repository.HubRepository;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import com.sparta.logistics.hub.hubstock.repository.HubStockRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * HubStock Kafka 통합 테스트
 *
 * - EmbeddedKafka: 실제 Kafka 브로커 없이 인메모리로 동작
 * - DirtiesContext: 테스트마다 컨텍스트 초기화 (토픽 오염 방지)
 * - CompanyClient: FeignClient라 실제 호출 불가 → MockBean으로 대체
 *
 * 의존성 필요:
 *   testImplementation 'org.springframework.kafka:spring-kafka-test'
 *   testImplementation 'org.awaitility:awaitility'  ← 비동기 검증용
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.ORDER_CREATED,
                KafkaTopics.STOCK_RESERVED,
                KafkaTopics.STOCK_RESERVATION_FAILED,
                KafkaTopics.RESTORE_STOCK_COMMAND,
                KafkaTopics.STOCK_RESTORED_ACK,
                KafkaTopics.DELIVERY_CREATION_FAILED,
                KafkaTopics.DELIVERY_STARTED,
                KafkaTopics.HUB_STOCK_UPDATED,
                KafkaTopics.STOCK_RESTORATION_FAILED,
        }
)
class HubStockKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HubStockRepository hubStockRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> stockReservedConsumer;
    private Consumer<String, String> stockReservationFailedConsumer;
    private Consumer<String, String> stockRestoredAckConsumer;
    private Consumer<String, String> hubStockUpdatedConsumer;
    private Consumer<String, String> stockRestorationFailedConsumer;

    // FeignClient는 실제 서버 없이 호출 불가 → Mock
    @MockitoBean
    private CompanyClient companyClient;

    private UUID hubId;
    private UUID productId;
    private UUID orderId;
    private UUID eventId;
    private UUID receiverCompanyId;
    private UUID destinationHubId;

    @BeforeEach
    void setUp() {

        hubId = UUID.randomUUID();
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        receiverCompanyId = UUID.randomUUID();
        destinationHubId = UUID.randomUUID();

        // Hub 저장 (name unique 제약으로 인해 매 테스트마다 다른 이름 사용)
        Hub hub = Hub.create("테스트 허브 " + UUID.randomUUID(), "서울시 강남구", new BigDecimal("37.5"), new BigDecimal("127.0"));
        hub = hubRepository.save(hub);
        hubId = hub.getId();

        // HubStock 저장 (재고 100개)
        HubStock hubStock = HubStock.create(hub, productId, 100);
        hubStockRepository.save(hubStock);

        // CompanyClient 모킹: receiverCompanyId → destinationHubId 반환
        // FeignClient는 실제 서버 없이 호출 불가하므로 Mock으로 대체
        CompanyResponse companyResponse = new CompanyResponse(destinationHubId);
        when(companyClient.getCompany(receiverCompanyId)).thenReturn(companyResponse);

        // stock.reserved 검증용 테스트 Consumer
        // HubStockEventConsumer(@KafkaListener)와 별개로 발행된 메시지를 직접 읽기 위해 생성
        Map<String, Object> consumerProps1 = KafkaTestUtils.consumerProps("test-group-reserved", "true", embeddedKafkaBroker);
        stockReservedConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps1, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(stockReservedConsumer, KafkaTopics.STOCK_RESERVED);

        // stock.reservation.failed 검증용 테스트 Consumer
        Map<String, Object> consumerProps2 = KafkaTestUtils.consumerProps("test-group-failed", "true", embeddedKafkaBroker);
        stockReservationFailedConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps2, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(stockReservationFailedConsumer, KafkaTopics.STOCK_RESERVATION_FAILED);

        // stock.restored.ack 검증용 테스트 Consumer
        Map<String, Object> consumerProps3 = KafkaTestUtils.consumerProps("test-group-ack", "true", embeddedKafkaBroker);
        stockRestoredAckConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps3, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(stockRestoredAckConsumer, KafkaTopics.STOCK_RESTORED_ACK);

        Map<String, Object> consumerProps4 = KafkaTestUtils.consumerProps("test-group-hub-updated", "true", embeddedKafkaBroker);
        hubStockUpdatedConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps4, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(hubStockUpdatedConsumer, KafkaTopics.HUB_STOCK_UPDATED);

        Map<String, Object> consumerProps5 = KafkaTestUtils.consumerProps("test-group-restoration-failed", "true", embeddedKafkaBroker);
        stockRestorationFailedConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps5, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(stockRestorationFailedConsumer, KafkaTopics.STOCK_RESTORATION_FAILED);
    }

    @AfterEach
    void tearDown() {
        stockReservedConsumer.close();
        stockReservationFailedConsumer.close();
        stockRestoredAckConsumer.close();
        hubStockUpdatedConsumer.close();
        stockRestorationFailedConsumer.close();
    }

    @Test
    @DisplayName("order.created 수신 시 재고 예약 후 stock.reserved 발행")
    void reserveStock_success() throws Exception {

        // given
        UUID orderItemId = UUID.randomUUID();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .receiverCompanyId(receiverCompanyId)
                .orderItems(List.of(
                        OrderItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .quantity(10)
                                .hubId(hubId)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, orderId.toString(), message).get();

        // then - 1) DB 재고 변경 확인 (비동기라 await 사용)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HubStock stock = hubStockRepository
                    .findByHubIdAndProductId(hubId, productId)
                    .orElseThrow();

            // available 100 → 90 (10 예약됨)
            assertThat(stock.getAvailable()).isEqualTo(90);
            // reserved 0 → 10
            assertThat(stock.getReserved()).isEqualTo(10);
        });

        // then - 2) stock.reserved 발행 확인
        // KafkaTestUtils로 발행된 메시지를 직접 읽어서 검증
        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(stockReservedConsumer, KafkaTopics.STOCK_RESERVED, Duration.ofSeconds(5));
        StockReservedEvent reservedEvent = objectMapper.readValue(record.value(), StockReservedEvent.class);
        assertThat(reservedEvent.getOrderId()).isEqualTo(orderId);
        assertThat(reservedEvent.getDestinationHubId()).isEqualTo(destinationHubId);
        assertThat(reservedEvent.getOrderItems()).hasSize(1);
        assertThat(reservedEvent.getOrderItems().get(0).getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고 부족 시 stock.reservation.failed 발행")
    void reserveStock_fail_insufficientStock() throws Exception {

        // given — 재고(100)보다 많은 수량 요청
        UUID orderItemId = UUID.randomUUID();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .receiverCompanyId(receiverCompanyId)
                .orderItems(List.of(
                        OrderItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .quantity(999)
                                .hubId(hubId)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, orderId.toString(), message).get();

        // then - 1) DB 재고 변경 없음 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HubStock stock = hubStockRepository
                    .findByHubIdAndProductId(hubId, productId)
                    .orElseThrow();

            // 재고 변동 없어야 함
            assertThat(stock.getAvailable()).isEqualTo(100);
            assertThat(stock.getReserved()).isEqualTo(0);
        });

        // then - 2) stock.reservation.failed 발행 확인
        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(stockReservationFailedConsumer, KafkaTopics.STOCK_RESERVATION_FAILED, Duration.ofSeconds(5));

        StockReservationFailedEvent failedEvent = objectMapper.readValue(record.value(), StockReservationFailedEvent.class);
        assertThat(failedEvent.getOrderId()).isEqualTo(orderId);
        assertThat(failedEvent.getProductId()).isEqualTo(productId);
    }

    @Test
    @DisplayName("restore.stock.command 수신 시 재고 복구 후 stock.restored.ack 발행")
    void restoreStock_success() throws Exception {

        // given - 먼저 30개 예약 상태 세팅 (restore는 reserved >= quantity 조건)
        HubStock stock = hubStockRepository.findByHubIdAndProductId(hubId, productId).orElseThrow();
        stock.reserve(30);
        hubStockRepository.save(stock);
        // available: 70, reserved: 30

        UUID orderItemId = UUID.randomUUID();

        RestoreStockCommand command = RestoreStockCommand.builder()
                .eventId(eventId)
                .orderId(orderId)
                .orderItems(List.of(
                        RestoreStockItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .hubId(hubId)
                                .quantity(30)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(command);

        // when
        kafkaTemplate.send(KafkaTopics.RESTORE_STOCK_COMMAND, orderId.toString(), message).get();

        // then - 1) DB 재고 복구 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HubStock updatedStock = hubStockRepository
                    .findByHubIdAndProductId(hubId, productId)
                    .orElseThrow();

            // available: 70 → 100 (30 복구됨), reserved: 30 → 0
            assertThat(updatedStock.getAvailable()).isEqualTo(100);
            assertThat(updatedStock.getReserved()).isEqualTo(0);
        });

        // then - 2) stock.restored.ack 발행 확인
        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(stockRestoredAckConsumer, KafkaTopics.STOCK_RESTORED_ACK, Duration.ofSeconds(5));

        StockRestoredAckEvent ackEvent = objectMapper.readValue(record.value(), StockRestoredAckEvent.class);
        assertThat(ackEvent.getOrderId()).isEqualTo(orderId);
        assertThat(ackEvent.getEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("delivery.creation.failed 수신 시 예약 재고 복구")
    void restoreOnDeliveryFailed_success() throws Exception {

        // given - 30개 예약 상태 세팅
        HubStock stock = hubStockRepository.findByHubIdAndProductId(hubId, productId).orElseThrow();
        stock.reserve(30);
        hubStockRepository.save(stock);
        // available: 70, reserved: 30

        UUID orderItemId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .deliveryId(deliveryId)
                .reason("배송 경로 없음")
                .itemsToRestore(List.of(
                        RestoreStockItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .hubId(hubId)
                                .quantity(30)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(KafkaTopics.DELIVERY_CREATION_FAILED, orderId.toString(), message).get();

        // then - DB 재고 복구 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HubStock updatedStock = hubStockRepository
                    .findByHubIdAndProductId(hubId, productId)
                    .orElseThrow();

            // available: 70 → 100, reserved: 30 → 0
            assertThat(updatedStock.getAvailable()).isEqualTo(100);
            assertThat(updatedStock.getReserved()).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("delivery.started 수신 시 예약 재고 차감")
    void deductReservedStock_success() throws Exception {

        // given - 30개 예약 상태 세팅
        HubStock stock = hubStockRepository.findByHubIdAndProductId(hubId, productId).orElseThrow();
        stock.reserve(30);
        hubStockRepository.save(stock);
        // available: 70, reserved: 30

        UUID orderItemId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();

        DeliveryStartedEvent event = DeliveryStartedEvent.builder()
                .eventId(eventId)
                .deliveryId(deliveryId)
                .orderId(orderId)
                .orderItems(List.of(
                        DeliveryOrderItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .hubId(hubId)
                                .quantity(30)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(KafkaTopics.DELIVERY_STARTED, deliveryId.toString(), message).get();

        // then - reserved 차감 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            HubStock updatedStock = hubStockRepository
                    .findByHubIdAndProductId(hubId, productId)
                    .orElseThrow();

            // available: 70 유지 (available 변동 없음)
            assertThat(updatedStock.getAvailable()).isEqualTo(70);
            // reserved: 30 → 0
            assertThat(updatedStock.getReserved()).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("재고 변경 시 hub.stock.updated 발행")
    void hubStockUpdated_publishedOnReserve() throws Exception {

        // given
        UUID orderItemId = UUID.randomUUID();

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .eventId(eventId)
                .orderId(orderId)
                .receiverCompanyId(receiverCompanyId)
                .orderItems(List.of(
                        OrderItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(productId)
                                .quantity(10)
                                .hubId(hubId)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(event);

        // when
        kafkaTemplate.send(KafkaTopics.ORDER_CREATED, orderId.toString(), message).get();

        // then - hub.stock.updated 발행 확인
        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(hubStockUpdatedConsumer, KafkaTopics.HUB_STOCK_UPDATED, Duration.ofSeconds(5));

        HubStockUpdatedEvent updatedEvent = objectMapper.readValue(record.value(), HubStockUpdatedEvent.class);
        assertThat(updatedEvent.getProductId()).isEqualTo(productId);
        assertThat(updatedEvent.getHubId()).isEqualTo(hubId);
        assertThat(updatedEvent.getAvailable()).isEqualTo(90);
    }

    @Test
    @DisplayName("restore.stock.command 수신 시 허브 재고 없으면 stock.restoration.failed 발행")
    void restoreStock_fail_hubStockNotFound() throws Exception {

        // given - 존재하지 않는 productId로 복구 시도
        UUID unknownProductId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();

        RestoreStockCommand command = RestoreStockCommand.builder()
                .eventId(eventId)
                .orderId(orderId)
                .orderItems(List.of(
                        RestoreStockItemPayload.builder()
                                .orderItemId(orderItemId)
                                .productId(unknownProductId)
                                .hubId(hubId)
                                .quantity(10)
                                .build()
                ))
                .build();

        String message = objectMapper.writeValueAsString(command);

        // when
        kafkaTemplate.send(KafkaTopics.RESTORE_STOCK_COMMAND, orderId.toString(), message).get();

        // then - stock.restoration.failed 발행 확인
        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(stockRestorationFailedConsumer, KafkaTopics.STOCK_RESTORATION_FAILED, Duration.ofSeconds(5));

        StockRestorationFailedEvent failedEvent = objectMapper.readValue(record.value(), StockRestorationFailedEvent.class);
        assertThat(failedEvent.getOrderId()).isEqualTo(orderId);
        assertThat(failedEvent.getEventId()).isEqualTo(eventId);
    }
}