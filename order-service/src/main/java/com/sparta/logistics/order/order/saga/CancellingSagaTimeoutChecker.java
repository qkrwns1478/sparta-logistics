package com.sparta.logistics.order.order.saga;

import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Orchestration Saga 안전망: CANCELLING 고착 감지
 * <p>
 * cancel.delivery.command 발행 후 응답 이벤트가 유실되거나
 * restore.stock.command 재시도가 한도(MAX_RESTORE_RETRY)를 초과한 경우
 * 주문이 CANCELLING 상태에 고착될 수 있음
 * <p>
 * 5분마다 실행하여 30분 이상 CANCELLING을 유지 중인 주문을 조회하고 log.warn을 남김
 * 자동 복구는 따로 없음
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class CancellingSagaTimeoutChecker {

    private static final long TIMEOUT_MINUTES = 30;

    private final OrderRepository orderRepository;

    @Scheduled(fixedDelay = 5 * 60 * 1000) // 5분마다 실행
    public void checkStuckCancellingOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        // CANCELLING 상태이면서 updatedAt이 30분 전인 주문 목록 조회
        List<Order> stuck = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.CANCELLING, threshold);

        for (Order order: stuck) {
            log.warn("[CancelSaga] CANCELLING 타임아웃 감지 orderId={} updatedAt={}", order.getId(), order.getUpdatedAt());
        }
    }
}
