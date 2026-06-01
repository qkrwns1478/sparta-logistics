package com.sparta.logistics.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kafka 이벤트 중복 소비 방지용 헬퍼
 * p_processed_event 테이블에 eventId를 insert하여 처리 여부를 추적함
 * REQUIRES_NEW로 컨슈머 트랜잭션과 분리하여, 컨슈머 롤백 시에도 dedup 레코드가 유지됨
 * 빈 등록은 OutboxAutoConfiguration이 담당함 (ProcessedEventRepository 빈이 있을 때만 활성화)
 * */
@Slf4j
@RequiredArgsConstructor
public class EventDeduplicator {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * 처음 수신한 eventId이면 DB에 기록하고 false를 반환함
     * 이미 처리된 eventId이면 true를 반환함 (컨슈머는 skip해야 함)
     * */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean isDuplicate(UUID eventId, String topic) {
        if (processedEventRepository.existsById(eventId)) {
            log.warn("[Dedup] 이미 처리된 이벤트 eventId={} topic={}", eventId, topic);
            return true;
        }
        try {
            processedEventRepository.saveAndFlush(ProcessedEvent.of(eventId, topic));
            return false;
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 다른 스레드가 먼저 insert한 경우
            log.warn("[Dedup] 동시 중복 감지 eventId={} topic={}", eventId, topic);
            return true;
        }
    }
}
