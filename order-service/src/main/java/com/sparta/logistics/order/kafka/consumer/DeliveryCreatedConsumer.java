package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Choreography Saga Step 1-4: delivery.created 이벤트 수신
 * <p>
 * DeliveryService가 배송 생성을 완료하면 이 이벤트가 발행된다.
 * 수신 시 해당 주문 상태를 ACCEPTED로 전이하고 deliveryId를 저장한다.
 * <p>
 * OrderService.acceptOrder()에서 PENDING 상태 여부를 확인하여 중복 처리를 방지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCreatedConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CREATED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(DeliveryCreatedEvent event) {
        log.info("[delivery.created] 수신 orderId={} deliveryId={}",
                event.getOrderId(), event.getDeliveryId());

        orderService.acceptOrder(event.getOrderId(), event.getDeliveryId());
    }
}
