package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.StockReservationFailedEvent;
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
 * Choreography Saga Step 2-1: stock.reservation.failed 이벤트 수신 (보상 트랜잭션)
 * <p>
 * HubService가 재고 부족 등으로 예약에 실패하면 이 이벤트가 발행됨
 * 수신 시 해당 주문을 즉시 CANCELLED 처리함
 * <p>
 * OrderService.cancelOrderByCompensation()에서 이미 CANCELLED인 경우 무시됨
 * <p>
 * 동시성 제어: CANCELLING 키 존재 시 skip (Orchestration Saga 취소 진행 중), 이외엔 PROCESSING 키 세팅
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReservationFailedConsumer {

    private final OrderService orderService;
    private final KafkaMessageParser parser;
    private final OrderLockManager orderLockManager;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESERVATION_FAILED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String message) {
        parser.parse(message, StockReservationFailedEvent.class).ifPresent(event -> {
            UUID orderId = event.getOrderId();

            if (isCancelling(orderId)) {
                log.info("[stock.reservation.failed] CANCELLING 진행 중 (skip) orderId={}", orderId);
                return;
            }
            orderLockManager.setStatusKey(orderId, OrderProcessStatus.PROCESSING);

            log.info("[stock.reservation.failed] 수신 orderId={} productId={} reason={}",
                    orderId, event.getProductId(), event.getReason());
            orderService.cancelOrderByCompensation(orderId, event.getReason());
        });
    }

    private boolean isCancelling(UUID orderId) {
        return orderLockManager.getStatusKey(orderId)
                .filter(s -> s == OrderProcessStatus.CANCELLING)
                .isPresent();
    }
}
