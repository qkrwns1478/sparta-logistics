package com.sparta.logistics.order.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Kafka 메시지 역직렬화 공통 유틸리티
 * <p>
 * 각 Consumer에서 raw JSON string를 ObjectMapper로 직접 역직렬화할 때 공통으로 사용함
 * 역직렬화 실패 시 빈 Optional을 반환하고 오류를 로깅하여 Kafka 불필요 재시도를 방지함
 **/
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaMessageParser {

    private final ObjectMapper objectMapper;

    public <T> Optional<T> parse(String message, Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(message, type));
        } catch (JsonProcessingException e) {
            log.error("[{}] 역직렬화 실패: {}", type.getSimpleName(), message, e);
            return Optional.empty();
        }
    }
}
