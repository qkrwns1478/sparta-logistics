package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCancellationFailedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Orchestration Saga 보상 Step 4-1: delivery.cancellation.failed 이벤트 수신
 * <p>
 * DeliveryService가 배송을 취소할 수 없을 때 발행함 (예: 이미 이동 중인 배송)
 * 수신 시 주문 상태를 CANCELLING → 이전 상태로 복구함
 * <p>
 * OrderService.handleDeliveryCancellationFailed()에서 CANCELLING 상태 여부를 확인하여 중복 처리를 방지함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCancellationFailedConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CANCELLATION_FAILED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(DeliveryCancellationFailedEvent event) {
        log.info("[delivery.cancellation.failed] 수신 orderId={} deliveryId={} reason={}",
                event.getOrderId(), event.getDeliveryId(), event.getReason());

        orderService.handleDeliveryCancellationFailed(event.getOrderId());
    }
}
