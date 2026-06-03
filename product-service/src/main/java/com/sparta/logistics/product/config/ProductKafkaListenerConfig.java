package com.sparta.logistics.product.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.CompanyDeleteRollbackEvent;
import com.sparta.logistics.common.kafka.event.CompanyDeletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Product Service Kafka Consumer 설정
 *
 * - company.deleted 이벤트 처리 실패 시 지정된 횟수만큼 재시도
 * - 최종 실패한 경우 보상 이벤트(company.delete.rollback)를 발행하고,
 *   원본 메시지는 DLQ로 전송
 */
@Slf4j
@Configuration
public class ProductKafkaListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler productKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(productKafkaErrorHandler); // Product 전용 에러 핸들러 장착
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public DefaultErrorHandler productKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {

        DeadLetterPublishingRecoverer dlqRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecordRecoverer customRecoverer = (record, exception) -> {
            log.error("[ProductErrorHandler] 최종 재시도 실패. DLQ 전송 및 보상 트랜잭션 시작. topic={}", record.topic());

            if ("company.deleted".equals(record.topic())) {
                try {
                    CompanyDeletedEvent event = objectMapper.readValue((String) record.value(), CompanyDeletedEvent.class);

                    String rollbackPayload = objectMapper.writeValueAsString(
                            new CompanyDeleteRollbackEvent(
                                    event.getCompanyId(),
                                    "[Product] 상품 삭제 최종 실패로 인한 보상 트랜잭션 발동"
                            )
                    );

                    kafkaTemplate.send("company.delete.rollback", event.getCompanyId().toString(), rollbackPayload);
                    log.info("[ProductErrorHandler] 보상 이벤트 발행 완료. companyId={}", event.getCompanyId());

                } catch (Exception ex) {
                    log.error("[ProductErrorHandler] 보상 이벤트 발행 실패", ex);
                }
            }

            dlqRecoverer.accept(record, exception);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(customRecoverer, new FixedBackOff(1000L, 3));

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.info("[Product-Retry] 재시도 횟수: {}, 원인: {}", deliveryAttempt, ex.getMessage());
        });

        return errorHandler;
    }
}