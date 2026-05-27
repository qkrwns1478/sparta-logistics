package com.sparta.logistics.order.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 수신 시 p_order_delivery에 deliveryId를 누적 저장함
 * 수신 건수가 totalDeliveryCount에 도달하면 주문 상태를 ACCEPTED로 전이함
 * <p>
 * OrderService.acceptOrder()에서 PENDING 상태 여부 및 중복 여부를 확인하여 멱등성을 보장함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCreatedConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CREATED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        DeliveryCreatedEvent event;
        try {
            event = objectMapper.readValue(message, DeliveryCreatedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("[delivery.created] 역직렬화 실패: {}", message, e);
            return;
        }

        log.info("[delivery.created] 수신 orderId={} deliveryId={} totalDeliveryCount={}",
                event.getOrderId(), event.getDeliveryId(), event.getTotalDeliveryCount());

        orderService.acceptOrder(event.getOrderId(), event.getDeliveryId(), event.getTotalDeliveryCount());
    }
}
