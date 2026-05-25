package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.DeliveryCancelledAckEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryCancelledAckConsumerTest {

    @InjectMocks
    private DeliveryCancelledAckConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID DELIVERY_ID = UUID.randomUUID();

    // delivery.cancelled.ack 수신 시 handleDeliveryCancelled()가 올바른 orderId로 호출되는지 검증
    @Test
    void consume_callsHandleDeliveryCancelledWithOrderId() {
        DeliveryCancelledAckEvent event = DeliveryCancelledAckEvent.builder()
                .eventId(UUID.randomUUID())
                .deliveryId(DELIVERY_ID)
                .orderId(ORDER_ID)
                .build();

        consumer.consume(event);

        verify(orderService).handleDeliveryCancelled(ORDER_ID);
    }
}
