package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.order.kafka.KafkaMessageParser;
import com.sparta.logistics.order.order.lock.OrderLockManager;
import com.sparta.logistics.order.order.lock.OrderProcessStatus;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryCreationFailedConsumerTest {

    @InjectMocks
    private DeliveryCreationFailedConsumer consumer;

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaMessageParser parser;

    @Mock
    private OrderLockManager orderLockManager;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID DELIVERY_ID = UUID.randomUUID();

    // CANCELLING 상태 키 존재 시 Choreography 보상 취소 호출 없이 skip되는지 검증
    // Orchestration Saga가 이미 취소를 진행 중이므로 Choreography 보상이 중복 실행되지 않아야 함
    @Test
    void consume_whenCancelling_skipsOrderService() {
        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .orderId(ORDER_ID).deliveryId(DELIVERY_ID).reason("배송 생성 실패").build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.CANCELLING));

        consumer.consume("{}");

        verify(orderService, never()).cancelOrderByCompensation(any(), any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
    }

    // CANCELLING 키가 없으면 PROCESSING 세팅 후 보상 취소가 호출되는지 검증
    @Test
    void consume_whenNotCancelling_setsProcessingAndCancelsOrder() {
        DeliveryCreationFailedEvent event = DeliveryCreationFailedEvent.builder()
                .orderId(ORDER_ID).deliveryId(DELIVERY_ID).reason("배송 생성 실패").build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.empty());

        consumer.consume("{}");

        verify(orderLockManager).setStatusKey(ORDER_ID, OrderProcessStatus.PROCESSING);
        verify(orderService).cancelOrderByCompensation(ORDER_ID, "배송 생성 실패");
    }

    // 메시지 파싱 실패 시 서비스 호출이 일어나지 않는지 검증
    @Test
    void consume_parseFailure_skipsAll() {
        doReturn(Optional.empty()).when(parser).parse(anyString(), any());

        consumer.consume("invalid-json");

        verify(orderLockManager, never()).getStatusKey(any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
        verify(orderService, never()).cancelOrderByCompensation(any(), eq("배송 생성 실패"));
    }
}
