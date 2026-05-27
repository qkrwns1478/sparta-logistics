package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.StockRestorationFailedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Orchestration Saga 보상 Step 4-2: stock.restoration.failed 이벤트 수신
 * <p>
 * HubService가 재고 복구에 실패했을 때 발행함
 * 수신 시 restore.stock.command를 재발행하여 재고 복구를 재시도함
 * <p>
 * OrderService.handleStockRestorationFailed()에서 CANCELLING 상태 여부를 확인하여 중복 처리를 방지함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestorationFailedConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.STOCK_RESTORATION_FAILED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(StockRestorationFailedEvent event) {
        log.info("[stock.restoration.failed] 수신 orderId={} reason={}",
                event.getOrderId(), event.getReason());

        orderService.handleStockRestorationFailed(event.getOrderId());
    }
}
