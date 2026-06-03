package com.sparta.logistics.company.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.CompanyDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 업체 삭제 이벤트 발행 Producer
 *
 * 업체 삭제 완료 후 CompanyDeletedEvent를 발행하여
 * Product Service의 상품 삭제 프로세스를 시작
 *
 * Saga Choreography 기반으로 서비스 간 직접 호출 없이
 * 이벤트를 통해 후속 작업을 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishCompanyDeleted(UUID companyId, UUID deletedBy) {

        CompanyDeletedEvent event = new CompanyDeletedEvent(
                companyId,
                deletedBy,
                LocalDateTime.now()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "company.deleted",
                    companyId.toString(),
                    payload
            );

            log.info("[CompanyEventProducer] 업체 삭제 이벤트 발행. companyId={}", companyId);

        } catch (Exception e) {
            log.error("[CompanyEventProducer] 이벤트 직렬화 실패", e);
            throw new RuntimeException(e);
        }
    }
}
