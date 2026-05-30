package com.sparta.logistics.order.outbox;

import com.sparta.logistics.common.outbox.OutboxEvent;
import com.sparta.logistics.common.outbox.OutboxEventRepository;
import com.sparta.logistics.common.outbox.OutboxEventStatus;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox 릴레이: PENDING 이벤트를 Kafka에 발행하고 상태를 갱신함
 * DB 커밋 후 Kafka 발행이 실패해도 재시도를 통해 at-least-once 보장
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // 컨텍스트 종료 시 릴레이 발화를 차단
    // create-drop DDL이 테이블을 삭제한 뒤 @Scheduled가 발화하는 race condition 방지
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Spring 컨텍스트 종료 시 가장 먼저 호출되어 이후 relay() 발화를 차단함
    @PreDestroy
    public void shutdown() {
        running.set(false);
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        if (!running.get()) return;
        List<OutboxEvent> pending =
                outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS); // 5초 초과 시 Kafka 장애로 간주
                event.markSent();
                log.info("[Outbox] 발행 완료 id={} topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.error("[Outbox] 발행 실패 id={} topic={} retryCount={}",
                        event.getId(), event.getTopic(), event.getRetryCount(), e);
                event.incrementRetry();
            }
            outboxEventRepository.save(event);
        }
    }
}
