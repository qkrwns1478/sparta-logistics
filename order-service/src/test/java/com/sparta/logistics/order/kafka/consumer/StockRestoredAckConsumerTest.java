package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.StockRestoredAckEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockRestoredAckConsumerTest {

    @InjectMocks
    private StockRestoredAckConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID ORDER_ID = UUID.randomUUID();

    // stock.restored.ack 수신 시 confirmOrderCancelled()가 올바른 orderId로 호출되는지 검증
    @Test
    void consume_callsConfirmOrderCancelledWithOrderId() {
        StockRestoredAckEvent event = StockRestoredAckEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(ORDER_ID)
                .build();

        consumer.consume(event);

        verify(orderService).confirmOrderCancelled(ORDER_ID);
    }
}
