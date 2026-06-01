package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.StockReservationFailedEvent;
import com.sparta.logistics.common.outbox.EventDeduplicator;
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
class StockReservationFailedConsumerTest {

    @InjectMocks
    private StockReservationFailedConsumer consumer;

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaMessageParser parser;

    @Mock
    private OrderLockManager orderLockManager;

    @Mock
    private EventDeduplicator deduplicator;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID PRODUCT_ID = UUID.randomUUID();
    private final UUID EVENT_ID = UUID.randomUUID();

    // 중복 이벤트이면 dedup 이후 모든 처리가 skip되는지 검증
    @Test
    void consume_whenDuplicate_skipsAll() {
        StockReservationFailedEvent event = StockReservationFailedEvent.builder()
                .eventId(EVENT_ID).orderId(ORDER_ID).productId(PRODUCT_ID).reason("재고 부족").build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(deduplicator.isDuplicate(eq(EVENT_ID), any())).thenReturn(true);

        consumer.consume("{}");

        verify(orderService, never()).cancelOrderByCompensation(any(), any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
    }

    // CANCELLING 상태 키 존재 시 Choreography 보상 취소 호출 없이 skip되는지 검증
    // Orchestration Saga가 이미 취소를 진행 중이므로 Choreography 보상이 중복 실행되지 않아야 함
    @Test
    void consume_whenCancelling_skipsOrderService() {
        StockReservationFailedEvent event = StockReservationFailedEvent.builder()
                .eventId(EVENT_ID).orderId(ORDER_ID).productId(PRODUCT_ID).reason("재고 부족").build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(deduplicator.isDuplicate(any(), any())).thenReturn(false);
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.CANCELLING));

        consumer.consume("{}");

        verify(orderService, never()).cancelOrderByCompensation(any(), any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
    }

    // CANCELLING 키가 없으면 PROCESSING 세팅 후 보상 취소가 호출되는지 검증
    @Test
    void consume_whenNotCancelling_setsProcessingAndCancelsOrder() {
        StockReservationFailedEvent event = StockReservationFailedEvent.builder()
                .eventId(EVENT_ID).orderId(ORDER_ID).productId(PRODUCT_ID).reason("재고 부족").build();
        doReturn(Optional.of(event)).when(parser).parse(anyString(), any());
        when(deduplicator.isDuplicate(any(), any())).thenReturn(false);
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.empty());

        consumer.consume("{}");

        verify(orderLockManager).setStatusKey(ORDER_ID, OrderProcessStatus.PROCESSING);
        verify(orderService).cancelOrderByCompensation(ORDER_ID, "재고 부족");
    }

    // 메시지 파싱 실패 시 서비스 호출이 일어나지 않는지 검증
    @Test
    void consume_parseFailure_skipsAll() {
        doReturn(Optional.empty()).when(parser).parse(anyString(), any());

        consumer.consume("invalid-json");

        verify(deduplicator, never()).isDuplicate(any(), any());
        verify(orderLockManager, never()).getStatusKey(any());
        verify(orderLockManager, never()).setStatusKey(any(), any());
        verify(orderService, never()).cancelOrderByCompensation(any(), eq("재고 부족"));
    }
}
