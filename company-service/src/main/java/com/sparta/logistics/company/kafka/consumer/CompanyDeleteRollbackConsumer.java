package com.sparta.logistics.company.kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.kafka.event.CompanyDeleteRollbackEvent;
import com.sparta.logistics.company.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업체 삭제 보상 이벤트 Consumer
 * Product Service 장애 시 발행된 롤백 이벤트를 처리하여 업체를 복구
 *
 * 시스템 예외는 Kafka 재시도 후 DLQ로 전송
 * 비즈니스 오류 및 JSON 파싱 오류는 재처리하지 않음
 *
 * 상품 삭제는 Product Service 트랜잭션 롤백으로 복구
 *  → Kafka 보상은 업체 복구에만 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyDeleteRollbackConsumer {

    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "company.delete.rollback",
            groupId = "company-service-rollback-consumer-v3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(String message, Acknowledgment acknowledgment) {

        CompanyDeleteRollbackEvent event = null;

        try {
            event = objectMapper.readValue(message, CompanyDeleteRollbackEvent.class);

            log.warn("[Rollback] 업체 삭제 롤백 시작. companyId={}", event.getCompanyId());

            int updated = companyRepository.restoreById(event.getCompanyId());
            if (updated == 0) {

                log.warn("[Rollback] 복구 대상 없음 or 이미 복구됨 companyId={}", event.getCompanyId());
            } else {

                log.info("[Rollback] 복구 성공 companyId={}", event.getCompanyId());
            }

            acknowledgment.acknowledge();
            log.info("[Rollback] 업체 삭제 롤백 완료. companyId={}", event.getCompanyId());

        } catch (BusinessException e) {

            log.warn("[Rollback] 롤백 비즈니스 실패 companyId={}, reason={}",
                    event != null ? event.getCompanyId() : null,
                    e.getMessage());

            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {

            log.error("[Rollback] JSON 파싱 실패. companyId={}",
                    event != null ? event.getCompanyId() : null, e);

            acknowledgment.acknowledge();

        } catch (Exception e) {

            log.error("[Rollback] 롤백 시스템 실패. companyId={}",
                    event != null ? event.getCompanyId() : null, e);

            throw e;
        }
    }
}