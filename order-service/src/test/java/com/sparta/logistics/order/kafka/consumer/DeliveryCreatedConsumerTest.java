package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryCreatedConsumerTest {

    @InjectMocks
    private DeliveryCreatedConsumer consumer;

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaMessageParser parser;

    @Mock
    private OrderLockManager orderLockManager;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID DELIVERY_ID = UUID.randomUUID();

    // CANCELLING 상태 키 존재 시 서비스 호출 없이 skip되는지 검증
    @Test
    void consume_whenCancelling_skipsOrderService() {
        DeliveryCreatedEvent event = DeliveryCreatedEvent.builder()
                .orderId(ORDER_ID).deliveryId(DELIVERY_ID).totalDeliveryCount(1).build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.CANCELLING));

        consumer.consume("{}");

        verify(orderService, never()).acceptOrder(any(), any(), anyInt());
        verify(orderLockManager, never()).setStatusKey(any(), any());
    }

    // CANCELLING 키가 없으면 PROCESSING 세팅 후 서비스가 호출되는지 검증
    @Test
    void consume_whenNotCancelling_setsProcessingAndCallsService() {
        DeliveryCreatedEvent event = DeliveryCreatedEvent.builder()
                .orderId(ORDER_ID).deliveryId(DELIVERY_ID).totalDeliveryCount(2).build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.empty());

        consumer.consume("{}");

        verify(orderLockManager).setStatusKey(ORDER_ID, OrderProcessStatus.PROCESSING);
        verify(orderService).acceptOrder(ORDER_ID, DELIVERY_ID, 2);
    }

    // 상태 키가 PROCESSING이면 CANCELLING이 아니므로 정상 처리되는지 검증
    @Test
    void consume_whenProcessing_doesNotSkip() {
        DeliveryCreatedEvent event = DeliveryCreatedEvent.builder()
                .orderId(ORDER_ID).deliveryId(DELIVERY_ID).totalDeliveryCount(1).build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.PROCESSING));

        consumer.consume("{}");

        verify(orderLockManager).setStatusKey(ORDER_ID, OrderProcessStatus.PROCESSING);
        verify(orderService).acceptOrder(eq(ORDER_ID), eq(DELIVERY_ID), eq(1));
    }

    // 메시지 파싱 실패 시 서비스 호출이 일어나지 않는지 검증
    @Test
    void consume_parseFailure_skipsAll() {
        doReturn(Optional.empty()).when(parser).parse(anyString(), any());

        consumer.consume("invalid-json");

        verify(orderLockManager, never()).getStatusKey(any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
        verify(orderService, never()).acceptOrder(any(), any(), anyInt());
    }
}
