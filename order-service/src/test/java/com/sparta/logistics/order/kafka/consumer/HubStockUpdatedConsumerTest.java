package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.event.HubStockUpdatedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HubStockUpdatedConsumerTest {

    @InjectMocks
    private HubStockUpdatedConsumer consumer;

    @Mock
    private OrderService orderService;

    private final UUID PRODUCT_ID = UUID.randomUUID();
    private final UUID HUB_ID = UUID.randomUUID();

    // hub.stock.updated 이벤트 수신 시 OrderService.syncSnapshot()이 올바른 이벤트로 호출되는지 검증
    @Test
    void consume_callsSyncSnapshotWithCorrectEvent() {
        HubStockUpdatedEvent event = HubStockUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .hubId(HUB_ID)
                .available(100)
                .hubStockVersion(3L)
                .build();

        consumer.consume(event);

        verify(orderService).syncSnapshot(event);
    }
}
