package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationFailedConsumerTest {

    @InjectMocks
    private DeliveryCreationFailedConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID DELIVERY_ID = UUID.randomUUID();

    // delivery.creation.failed 수신 시 cancelOrderByCompensation()이 올바른 인자로 호출되는지 검증
    @Test
    void consume_callsCancelOrderByCompensationWithCorrectArgs() {
        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(ORDER_ID)
                .deliveryId(DELIVERY_ID)
                .reason("배송 경로 없음")
                .build();

        consumer.consume(event);

        verify(orderService).cancelOrderByCompensation(ORDER_ID, "배송 경로 없음");
    }

    // deliveryId가 null(배송 생성 자체 실패)이어도 정상 처리되는지 검증
    @Test
    void consume_nullDeliveryId_callsCancelOrderByCompensation() {
        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .eventId(UUID.randomUUID())
                .orderId(ORDER_ID)
                .deliveryId(null)
                .reason("배송 담당자 없음")
                .build();

        consumer.consume(event);

        verify(orderService).cancelOrderByCompensation(ORDER_ID, "배송 담당자 없음");
    }
}
