package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.DeliveryCreatedEvent;
import com.sparta.logistics.common.outbox.EventDeduplicator;
import com.sparta.logistics.order.kafka.KafkaMessageParser;
import com.sparta.logistics.order.order.lock.OrderLockManager;
import com.sparta.logistics.order.order.lock.OrderProcessStatus;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Choreography Saga Step 1-4: delivery.created 이벤트 수신
 * <p>
 * DeliveryService가 배송 생성을 완료하면 이 이벤트가 발행된다.
 * 수신 시 p_order_delivery에 deliveryId를 누적 저장함
 * 수신 건수가 totalDeliveryCount에 도달하면 주문 상태를 ACCEPTED로 전이함
 * <p>
 * OrderService.acceptOrder()에서 PENDING 상태 여부 및 중복 여부를 확인하여 멱등성을 보장함
 * <p>
 * 동시성 제어: CANCELLING 키 존재 시 skip (취소 진행 중), 이외엔 PROCESSING 키 세팅
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCreatedConsumer {

    private final OrderService orderService;
    private final KafkaMessageParser parser;
    private final OrderLockManager orderLockManager;
    private final EventDeduplicator deduplicator;

    @KafkaListener(
            topics = KafkaTopics.DELIVERY_CREATED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        parser.parse(message, DeliveryCreatedEvent.class).ifPresent(event -> {
            if (deduplicator.isDuplicate(event.getEventId(), KafkaTopics.DELIVERY_CREATED)) {
                return;
            }
            UUID orderId = event.getOrderId();

            if (isCancelling(orderId)) {
                log.info("[delivery.created] CANCELLING 진행 중 (skip) orderId={}", orderId);
                return;
            }
            orderLockManager.setStatusKey(orderId, OrderProcessStatus.PROCESSING);
            try {
                log.info("[delivery.created] 수신 orderId={} deliveryId={} totalDeliveryCount={}",
                        orderId, event.getDeliveryId(), event.getTotalDeliveryCount());
                orderService.acceptOrder(orderId, event.getDeliveryId(), event.getTotalDeliveryCount());
            } finally {
                // acceptOrder() 후 주문이 PENDING/ACCEPTED가 되므로 즉시 해제해야 cancelOrder() 요청이 정상 처리됨
                orderLockManager.clearStatusKey(orderId);
            }
        });
    }

    private boolean isCancelling(UUID orderId) {
        return orderLockManager.getStatusKey(orderId)
                .filter(s -> s == OrderProcessStatus.CANCELLING)
                .isPresent();
    }
}
