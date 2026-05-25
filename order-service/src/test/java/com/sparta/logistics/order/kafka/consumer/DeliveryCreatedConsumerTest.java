package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryCreatedConsumerTest {

    @InjectMocks
    private DeliveryCreatedConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID DELIVERY_ID = UUID.randomUUID();

    // delivery.created 이벤트 수신 시 OrderService.acceptOrder()가 올바른 인자로 호출되는지 검증
    @Test
    void consume_callsAcceptOrderWithCorrectArgs() {
        DeliveryCreatedEvent event = DeliveryCreatedEvent.builder()
                .deliveryId(DELIVERY_ID)
                .orderId(ORDER_ID)
                .sourceHubId(UUID.randomUUID())
                .destinationHubId(UUID.randomUUID())
                .companyDeliveryManagerId(null)
                .build();

        consumer.consume(event);

        verify(orderService).acceptOrder(ORDER_ID, DELIVERY_ID);
    }
}
