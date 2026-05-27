package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.StockReservationFailedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockReservationFailedConsumerTest {

    @InjectMocks
    private StockReservationFailedConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID PRODUCT_ID = UUID.randomUUID();

    // stock.reservation.failed 수신 시 cancelOrderByCompensation()이 올바른 인자로 호출되는지 검증
    @Test
    void consume_callsCancelOrderByCompensationWithCorrectArgs() {
        StockReservationFailedEvent event = StockReservationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(ORDER_ID)
                .productId(PRODUCT_ID)
                .reason("재고 부족")
                .build();

        consumer.consume(event);

        verify(orderService).cancelOrderByCompensation(ORDER_ID, "재고 부족");
    }
}
