package com.sparta.logistics.order.kafka.consumer;

import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.kafka.event.HubStockUpdatedEvent;
import com.sparta.logistics.order.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * hub.stock.updated 이벤트 수신
 * <p>
 * HubService에서 재고 변경이 발생할 때마다 발행하는 이벤트를 수신함
 * 수신 시 OrderService를 통해 ProductStockSnapshot을 최신 상태로 갱신함
 * <p>
 * hubStockVersion 비교 → OrderService.syncSnapshot()에서 구버전 이벤트를 무시함
 * 파티션 키: productId (동일 상품의 재고 이벤트가 순서 보장됨)
 * */
@Slf4j
@Component
@RequiredArgsConstructor
public class HubStockUpdatedConsumer {

    private final OrderService orderService;

    @KafkaListener(
            topics = KafkaTopics.HUB_STOCK_UPDATED,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(HubStockUpdatedEvent event) {
        log.info("[hub.stock.updated] 수신 productId={} hubId={} available={} version={}",
                event.getProductId(), event.getHubId(), event.getAvailable(), event.getHubStockVersion());

        orderService.syncSnapshot(event);
    }
}
