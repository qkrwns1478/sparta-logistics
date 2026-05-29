package com.sparta.logistics.order.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.OrderCreatedEvent;
import com.sparta.logistics.common.kafka.event.OrderItemPayload;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.orderitem.entity.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderEventPublisher(kafkaTemplate, objectMapper);
    }

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID ORDER_ITEM_ID = UUID.randomUUID();
    private final UUID PRODUCT_ID = UUID.randomUUID();
    private final UUID HUB_ID = UUID.randomUUID();
    private final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private final UUID RECEIVER_COMPANY_ID = UUID.randomUUID();
    private final UUID SOURCE_HUB_ID = UUID.randomUUID();
    private final UUID DESTINATION_HUB_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final LocalDateTime DUE_DATE = LocalDateTime.now().plusDays(7);

    // publishOrderCreated() 호출 시 OrderItem이 OrderItemPayload로 올바르게 매핑되어 발행되는지 검증
    @Test
    void publishOrderCreated_mapsOrderItemsCorrectly() throws Exception {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);

        OrderItem item = OrderItem.create(order, PRODUCT_ID, "테스트 상품", 10_000L, 3, HUB_ID);
        ReflectionTestUtils.setField(item, "id", ORDER_ITEM_ID);
        order.addOrderItem(item);

        publisher.publishOrderCreated(order, SOURCE_HUB_ID, DESTINATION_HUB_ID);

        // KafkaTemplate에 전달된 raw JSON string 캡처
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_CREATED), eq(ORDER_ID.toString()), valueCaptor.capture());

        // JSON string → OrderCreatedEvent 역직렬화 후 필드 검증
        OrderCreatedEvent event = objectMapper.readValue(valueCaptor.getValue(), OrderCreatedEvent.class);
        assertThat(event.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(event.getRequesterCompanyId()).isEqualTo(REQUESTER_COMPANY_ID);
        assertThat(event.getReceiverCompanyId()).isEqualTo(RECEIVER_COMPANY_ID);
        assertThat(event.getSourceHubId()).isEqualTo(SOURCE_HUB_ID);
        assertThat(event.getDestinationHubId()).isEqualTo(DESTINATION_HUB_ID);
        assertThat(event.getOrderItems()).hasSize(1);

        // orderItemId, productId, quantity, hubId 포함 여부 확인
        OrderItemPayload payload = event.getOrderItems().get(0);
        assertThat(payload.getOrderItemId()).isEqualTo(ORDER_ITEM_ID);
        assertThat(payload.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(payload.getQuantity()).isEqualTo(3);
        assertThat(payload.getHubId()).isEqualTo(HUB_ID);
    }
}
