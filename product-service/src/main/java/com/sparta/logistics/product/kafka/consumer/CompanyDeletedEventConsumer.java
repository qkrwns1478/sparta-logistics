package com.sparta.logistics.product.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.CompanyDeletedEvent;
import com.sparta.logistics.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 업체 삭제 이벤트 Consumer
 *
 * Company Service에서 발행한
 * CompanyDeletedEvent를 수신하여
 * 해당 업체의 상품을 삭제
 *
 * 처리 실패 시 예외를 전파하여
 * Kafka ErrorHandler가 재시도 및
 * 보상 트랜잭션을 수행하도록 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyDeletedEventConsumer {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "company.deleted",
            groupId = "product-service-company-consumer-v3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(String message, Acknowledgment acknowledgment) throws Exception {

        try {

            // 1. JSON → Object 변환
            CompanyDeletedEvent event = objectMapper.readValue(message, CompanyDeletedEvent.class);
            log.info("[CompanyDeletedConsumer] 업체 삭제 이벤트 수신. companyId={}", event.getCompanyId());

            // 2. 비즈니스 로직 실행
            productService.deleteAllByCompanyId(event.getCompanyId());
            log.info("[CompanyDeletedConsumer] 상품 일괄 삭제 완료. companyId={}", event.getCompanyId());

            // 3. 성공 시에만 즉시 커밋 (Ack)
            acknowledgment.acknowledge();

        } catch (Exception e) {

            log.error("[CompanyDeletedConsumer] 상품 삭제 실패. ErrorHandler로 위임. message={}", e.getMessage());

            // 4. 예외를 외부로 던져서 Kafka 에러 핸들러가 캐치하여
            //    설정된 BackOff(1초 간격, 총 3회)에 따라 재시도를 수행
            throw e;
        }
    }
}
