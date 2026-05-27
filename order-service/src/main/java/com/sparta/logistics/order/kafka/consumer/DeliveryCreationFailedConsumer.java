package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCreationFailedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Choreography Saga Step 2-2: delivery.creation.failed 이벤트 수신 (보상 트랜잭션)
 * <p>
 * DeliveryService가 배송 생성에 실패하면 이 이벤트가 발행됨
 * HubService와 OrderService 모두 구독함:
 * - HubService: 재고 예약 복구 (CANCEL_RESTORE)
 * - OrderService(여기): 주문을 즉시 CANCELLED 처리
 * <p>
 * OrderService.cancelOrderByCompensation()에서 이미 CANCELLED인 경우 무시함
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCreationFailedConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CREATION_FAILED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(DeliveryCreationFailedEvent event) {
        log.info("[delivery.creation.failed] 수신 orderId={} deliveryId={} reason={}",
                event.getOrderId(), event.getDeliveryId(), event.getReason());

        orderService.cancelOrderByCompensation(event.getOrderId(), event.getReason());
    }
}
