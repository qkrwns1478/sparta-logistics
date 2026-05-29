package com.sparta.logistics.order.outbox;

import com.sparta.logistics.common.outbox.OutboxEvent;
import com.sparta.logistics.common.outbox.OutboxEventRepository;
import com.sparta.logistics.common.outbox.OutboxEventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void relay() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
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
