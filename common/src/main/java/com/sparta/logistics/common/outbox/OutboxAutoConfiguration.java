package com.sparta.logistics.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/**
 * OutboxEventRepository 빈이 등록된 서비스(= p_outbox 테이블을 갖는 서비스)에서만
 * OutboxEventPublisher를 활성함
 * Auto-configuration은 사용자 정의 빈(@Configuration)이 모두 처리된 후 실행되므로
 * &#064;ConditionalOnBean  조건 평가 시점에 JPA 레포지토리 빈 정의가 이미 존재함이 보장됨
 * */
@AutoConfiguration
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxEventRepository.class)
    public OutboxEventPublisher outboxEventPublisher(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        return new OutboxEventPublisher(outboxEventRepository, objectMapper);
    }

    @Bean
    @ConditionalOnBean(ProcessedEventRepository.class)
    public EventDeduplicator eventDeduplicator(ProcessedEventRepository processedEventRepository) {
        return new EventDeduplicator(processedEventRepository);
    }
}