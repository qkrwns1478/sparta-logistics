package com.sparta.logistics.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 호출 시점 트랜잭션에 참여해 Outbox 이벤트를 DB에 저장하는 공통 컴포넌트
 * 실제 Kafka 발행은 OutboxEventRelay가 별도 스케줄로 담당함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * 호출 시점의 트랜잭션에 참여하여 Outbox 이벤트를 DB에 저장함
     * 반드시 @Transactional 컨텍스트 안에서 호출해야 엔티티 저장과 원자적으로 커밋됨
     **/
    public void publish(String topic, String aggregateId, String aggregateType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(OutboxEvent.of(topic, aggregateId, aggregateType, json));
            log.debug("[Outbox] 이벤트 저장 topic={} aggregateId={}", topic, aggregateId);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] 직렬화 실패 topic={} aggregateId={}", topic, aggregateId, e);
            throw new IllegalStateException("Outbox 이벤트 직렬화 실패", e);
        }
    }
}
