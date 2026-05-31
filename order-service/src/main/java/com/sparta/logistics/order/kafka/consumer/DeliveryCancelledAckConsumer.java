package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCancelledAckEvent;
import com.sparta.logistics.order.kafka.KafkaMessageParser;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Orchestration Saga Step 3-3: delivery.cancelled.ack 이벤트 수신
 * <p>
 * DeliveryService가 배송 취소를 완료하면 이 이벤트가 발행됨
 * 수신 시 restore.stock.command를 발행하여 HubService에 재고 복구를 명령함
 * <p>
 * 멱등성: OrderService.handleDeliveryCancelled()에서 CANCELLING 상태 여부를 확인하여 중복 처리를 방지함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCancelledAckConsumer {

    private final OrderService orderService;
    private final KafkaMessageParser parser;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CANCELLED_ACK,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        parser.parse(message, DeliveryCancelledAckEvent.class).ifPresent(event -> {
            log.info("[delivery.cancelled.ack] 수신 orderId={} deliveryId={}",
                    event.getOrderId(), event.getDeliveryId());
            orderService.handleDeliveryCancelled(event.getOrderId());
        });
    }
}
