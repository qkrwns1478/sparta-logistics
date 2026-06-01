package com.sparta.logistics.order.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.*;
import com.sparta.logistics.order.client.CompanyServiceClient;
import com.sparta.logistics.order.client.ProductServiceClient;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.lock.OrderLockManager;
import com.sparta.logistics.order.order.repository.OrderRepository;
import com.sparta.logistics.order.order.service.OrderService;
import com.sparta.logistics.order.orderitem.entity.OrderItem;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Order Saga Kafka 통합 테스트
 * <p>
 * EmbeddedKafka: 실제 Kafka 브로커 없이 인메모리로 동작
 * DirtiesContext: 테스트마다 컨텍스트 초기화 (토픽 오염 방지)
 * OrderLockManager: Redis 기반이라 실제 연결 불가 → MockitoBean으로 대체
 * CompanyServiceClient, ProductServiceClient: FeignClient라 실제 호출 불가 → MockitoBean으로 대체
 **/
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=true"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                KafkaTopics.ORDER_CREATED,
                KafkaTopics.DELIVERY_CREATED,
                KafkaTopics.DELIVERY_CREATION_FAILED,
                KafkaTopics.STOCK_RESERVATION_FAILED,
                KafkaTopics.CANCEL_DELIVERY_COMMAND,
                KafkaTopics.DELIVERY_CANCELLED_ACK,
                KafkaTopics.DELIVERY_CANCELLATION_FAILED,
                KafkaTopics.RESTORE_STOCK_COMMAND,
                KafkaTopics.STOCK_RESTORED_ACK,
                KafkaTopics.STOCK_RESTORATION_FAILED,
                KafkaTopics.HUB_STOCK_UPDATED,
        }
)
class OrderSagaKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") // 오토와이어링 할 수 없다는 IDE 에러 무시
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private CompanyServiceClient companyServiceClient;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    @MockitoBean
    private OrderLockManager orderLockManager;

    private Consumer<String, String> cancelDeliveryCommandConsumer;
    private Consumer<String, String> restoreStockCommandConsumer;

    private UUID testOrderId;
    private final UUID testUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(orderLockManager.getStatusKey(any())).thenReturn(Optional.empty());
        when(orderLockManager.incrementAndGetRestoreRetry(any())).thenReturn(1);

        Map<String, Object> cancelProps = KafkaTestUtils.consumerProps("test-cancel-cmd", "true", embeddedKafkaBroker);
        cancelDeliveryCommandConsumer = new DefaultKafkaConsumerFactory<>(
                cancelProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        assignAndSeekToEnd(cancelDeliveryCommandConsumer, KafkaTopics.CANCEL_DELIVERY_COMMAND);

        Map<String, Object> restoreProps = KafkaTestUtils.consumerProps("test-restore-cmd", "true", embeddedKafkaBroker);
        restoreStockCommandConsumer = new DefaultKafkaConsumerFactory<>(
                restoreProps, new StringDeserializer(), new StringDeserializer()
        ).createConsumer();
        assignAndSeekToEnd(restoreStockCommandConsumer, KafkaTopics.RESTORE_STOCK_COMMAND);
    }

    private void assignAndSeekToEnd(Consumer<String, String> consumer, String topic) {
        TopicPartition tp = new TopicPartition(topic, 0);
        consumer.assign(Collections.singletonList(tp));
        long endOffset = consumer.endOffsets(Collections.singletonList(tp)).getOrDefault(tp, 0L);
        consumer.seek(tp, endOffset);
    }

    @AfterEach
    void tearDown() {
        cancelDeliveryCommandConsumer.close();
        restoreStockCommandConsumer.close();
    }

    // ===== Choreography Saga (주문 생성) =====

    @Test
    @DisplayName("delivery.created 수신 시 PENDING 주문이 ACCEPTED로 전이")
    void onDeliveryCreated_pendingOrder_transitionsToAccepted() throws Exception {
        savePendingOrder();
        UUID deliveryId = UUID.randomUUID();

        DeliveryCreatedEvent event = DeliveryCreatedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .deliveryId(deliveryId)
                .totalDeliveryCount(1)
                .build();

        kafkaTemplate.send(KafkaTopics.DELIVERY_CREATED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
            assertThat(updated.getDeliveryId()).isEqualTo(deliveryId);
        });
    }

    @Test
    @DisplayName("stock.reservation.failed 수신 시 PENDING 주문이 CANCELLED로 전이")
    void onStockReservationFailed_pendingOrder_transitionsToCancelled() throws Exception {
        savePendingOrder();

        StockReservationFailedEvent event = StockReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .productId(UUID.randomUUID())
                .reason("재고 부족")
                .build();

        kafkaTemplate.send(KafkaTopics.STOCK_RESERVATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    @DisplayName("delivery.creation.failed 수신 시 PENDING 주문이 CANCELLED로 전이")
    void onDeliveryCreationFailed_pendingOrder_transitionsToCancelled() throws Exception {
        savePendingOrder();

        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .reason("배송 경로 없음")
                .itemsToRestore(List.of())
                .build();

        kafkaTemplate.send(KafkaTopics.DELIVERY_CREATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    // ===== Orchestration Saga (주문 취소) =====

    @Test
    @DisplayName("cancelOrder() 호출 시 CANCELLING 전이 + cancel.delivery.command 발행")
    void cancelOrder_acceptedOrder_transitionsToCancellingAndPublishesCommand() throws Exception {
        UUID deliveryId = UUID.randomUUID();
        saveAcceptedOrderWithItem(UUID.randomUUID(), UUID.randomUUID(), deliveryId);

        orderService.cancelOrder(testOrderId, "취소 사유", testUserId, Role.MASTER, null);

        Order updated = orderRepository.findById(testOrderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLING);

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(cancelDeliveryCommandConsumer, KafkaTopics.CANCEL_DELIVERY_COMMAND, Duration.ofSeconds(5));
        CancelDeliveryCommand cmd = objectMapper.readValue(record.value(), CancelDeliveryCommand.class);
        assertThat(cmd.getOrderId()).isEqualTo(testOrderId);
        assertThat(cmd.getDeliveryId()).isEqualTo(deliveryId);
    }

    @Test
    @DisplayName("delivery.cancelled.ack 수신 시 CANCELLING 주문에서 restore.stock.command 발행")
    void onDeliveryCancelledAck_cancellingOrder_publishesRestoreStockCommand() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        saveCancellingOrderWithItem(productId, hubId);

        DeliveryCancelledAckEvent event = DeliveryCancelledAckEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .deliveryId(UUID.randomUUID())
                .build();

        kafkaTemplate.send(KafkaTopics.DELIVERY_CANCELLED_ACK, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(restoreStockCommandConsumer, KafkaTopics.RESTORE_STOCK_COMMAND, Duration.ofSeconds(5));
        RestoreStockCommand cmd = objectMapper.readValue(record.value(), RestoreStockCommand.class);
        assertThat(cmd.getOrderId()).isEqualTo(testOrderId);
        assertThat(cmd.getOrderItems()).hasSize(1);
        assertThat(cmd.getOrderItems().get(0).getProductId()).isEqualTo(productId);
        assertThat(cmd.getOrderItems().get(0).getHubId()).isEqualTo(hubId);
    }

    @Test
    @DisplayName("stock.restored.ack 수신 시 CANCELLING 주문이 CANCELLED로 전이")
    void onStockRestoredAck_cancellingOrder_transitionsToCancelled() throws Exception {
        saveCancellingOrderWithItem(UUID.randomUUID(), UUID.randomUUID());

        StockRestoredAckEvent event = StockRestoredAckEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .build();

        kafkaTemplate.send(KafkaTopics.STOCK_RESTORED_ACK, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        });
    }

    @Test
    @DisplayName("delivery.cancellation.failed 수신 시 deliveryId 없는 CANCELLING 주문이 PENDING으로 복구")
    void onDeliveryCancellationFailed_noDeliveryId_revertsToPending() throws Exception {
        // PENDING → CANCELLING (deliveryId = null)
        saveCancellingOrderWithItem(UUID.randomUUID(), UUID.randomUUID());

        DeliveryCancellationFailedEvent event = DeliveryCancellationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .reason("배송 이동 중 취소 불가")
                .build();

        kafkaTemplate.send(KafkaTopics.DELIVERY_CANCELLATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.PENDING);
        });
    }

    @Test
    @DisplayName("delivery.cancellation.failed 수신 시 deliveryId 있는 CANCELLING 주문이 ACCEPTED로 복구")
    void onDeliveryCancellationFailed_withDeliveryId_revertsToAccepted() throws Exception {
        // PENDING → ACCEPTED (deliveryId 연결됨) → CANCELLING
        saveCancellingAcceptedOrderWithItem(UUID.randomUUID(), UUID.randomUUID());

        DeliveryCancellationFailedEvent event = DeliveryCancellationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .reason("배송 이동 중 취소 불가")
                .build();

        kafkaTemplate.send(KafkaTopics.DELIVERY_CANCELLATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updated = orderRepository.findById(testOrderId).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        });
    }

    @Test
    @DisplayName("stock.restoration.failed 수신 시 재시도 한도 내에서 restore.stock.command 재발행")
    void onStockRestorationFailed_withinRetryLimit_republishesCommand() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID hubId = UUID.randomUUID();
        saveCancellingOrderWithItem(productId, hubId);
        when(orderLockManager.incrementAndGetRestoreRetry(any())).thenReturn(OrderLockManager.MAX_RESTORE_RETRY);

        StockRestorationFailedEvent event = StockRestorationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .reason("재고 없음")
                .build();

        kafkaTemplate.send(KafkaTopics.STOCK_RESTORATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(restoreStockCommandConsumer, KafkaTopics.RESTORE_STOCK_COMMAND, Duration.ofSeconds(5));
        RestoreStockCommand cmd = objectMapper.readValue(record.value(), RestoreStockCommand.class);
        assertThat(cmd.getOrderId()).isEqualTo(testOrderId);
    }

    @Test
    @DisplayName("stock.restoration.failed 수신 시 최대 재시도 초과 시 재발행하지 않음")
    void onStockRestorationFailed_overRetryLimit_doesNotRepublish() throws Exception {
        saveCancellingOrderWithItem(UUID.randomUUID(), UUID.randomUUID());
        when(orderLockManager.incrementAndGetRestoreRetry(any()))
                .thenReturn(OrderLockManager.MAX_RESTORE_RETRY + 1);

        StockRestorationFailedEvent event = StockRestorationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(testOrderId)
                .reason("재고 없음")
                .build();

        kafkaTemplate.send(KafkaTopics.STOCK_RESTORATION_FAILED, testOrderId.toString(),
                objectMapper.writeValueAsString(event)).get();

        assertThat(restoreStockCommandConsumer.poll(Duration.ofSeconds(2)).isEmpty()).isTrue();
    }

    // ===== Helper =====

    private void savePendingOrder() {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(), testUserId,
                LocalDateTime.now().plusDays(7), null
        );
        testOrderId = orderRepository.save(order).getId();
    }

    private void saveAcceptedOrderWithItem(UUID productId, UUID hubId, UUID deliveryId) {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(), testUserId,
                LocalDateTime.now().plusDays(7), null
        );
        order.addOrderItem(OrderItem.create(order, productId, "테스트 상품", 10_000L, 5, hubId));
        order.calculateTotalAmount();
        order.accept();
        order.linkDelivery(deliveryId);
        testOrderId = orderRepository.save(order).getId();
    }

    private void saveCancellingOrderWithItem(UUID productId, UUID hubId) {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(), testUserId,
                LocalDateTime.now().plusDays(7), null
        );
        order.addOrderItem(OrderItem.create(order, productId, "테스트 상품", 10_000L, 5, hubId));
        order.calculateTotalAmount();
        order.startCancelling(testUserId, "테스트 취소");
        testOrderId = orderRepository.save(order).getId();
    }

    private void saveCancellingAcceptedOrderWithItem(UUID productId, UUID hubId) {
        Order order = Order.create(
                UUID.randomUUID(), UUID.randomUUID(), testUserId,
                LocalDateTime.now().plusDays(7), null
        );
        order.addOrderItem(OrderItem.create(order, productId, "테스트 상품", 10_000L, 5, hubId));
        order.calculateTotalAmount();
        order.accept();
        order.linkDelivery(UUID.randomUUID()); // deliveryId 존재 → 복구 시 ACCEPTED로 복원됨
        order.startCancelling(testUserId, "테스트 취소");
        testOrderId = orderRepository.save(order).getId();
    }
}
