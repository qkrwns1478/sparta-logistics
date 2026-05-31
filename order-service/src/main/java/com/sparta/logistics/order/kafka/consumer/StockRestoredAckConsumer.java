package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.StockRestoredAckEvent;
import com.sparta.logistics.order.kafka.KafkaMessageParser;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Orchestration Saga Step 3-5: stock.restored.ack 이벤트 수신
 * <p>
 * HubService가 재고 복구를 완료하면 이 이벤트가 발행됨
 * 수신 시 주문 상태를 CANCELLED로 확정함 (Orchestration Saga 완료)
 * <p>
 * OrderService.confirmOrderCancelled()에서 CANCELLING 상태 여부를 확인해서 중복 처리 방지
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestoredAckConsumer {

    private final OrderService orderService;
    private final KafkaMessageParser parser;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESTORED_ACK,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        parser.parse(message, StockRestoredAckEvent.class).ifPresent(event -> {
            log.info("[stock.restored.ack] 수신 orderId={}", event.getOrderId());
            orderService.confirmOrderCancelled(event.getOrderId());
        });
    }
}
