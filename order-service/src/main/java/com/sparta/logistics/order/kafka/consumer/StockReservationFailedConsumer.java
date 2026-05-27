package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.StockReservationFailedEvent;
import com.sparta.logistics.order.kafka.KafkaMessageParser;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Choreography Saga Step 2-1: stock.reservation.failed 이벤트 수신 (보상 트랜잭션)
 * <p>
 * HubService가 재고 부족 등으로 예약에 실패하면 이 이벤트가 발행됨
 * 수신 시 해당 주문을 즉시 CANCELLED 처리함
 * <p>
 * OrderService.cancelOrderByCompensation()에서 이미 CANCELLED인 경우 무시됨
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReservationFailedConsumer {

    private final OrderService orderService;
    private final KafkaMessageParser parser;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_FAILED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        parser.parse(message, StockReservationFailedEvent.class).ifPresent(event -> {
            log.info("[stock.reservation.failed] 수신 orderId={} productId={} reason={}",
                    event.getOrderId(), event.getProductId(), event.getReason());
            orderService.cancelOrderByCompensation(event.getOrderId(), event.getReason());
        });
    }
}
