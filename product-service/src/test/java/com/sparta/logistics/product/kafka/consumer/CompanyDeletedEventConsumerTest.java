package com.sparta.logistics.product.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.logistics.common.kafka.event.CompanyDeletedEvent;
import com.sparta.logistics.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CompanyDeletedEventConsumerTest {

    @Mock private ProductService productService;

    // LocalDateTime 직렬화 및 역직렬화를 지원하는 JavaTimeModule 장착
    @Spy private ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @InjectMocks private CompanyDeletedEventConsumer companyDeletedEventConsumer;

    @Test
    @DisplayName("업체 삭제 이벤트를 수신하면 소속 상품을 일괄 삭제하고 ACK를 커밋한다")
    void consume_deleteProducts_success() throws Exception {
        // given
        UUID companyId = UUID.randomUUID();
        CompanyDeletedEvent event = new CompanyDeletedEvent(companyId, UUID.randomUUID(), LocalDateTime.now());
        String message = objectMapper.writeValueAsString(event); // 이 단계에서 발생하던 에러가 해결됩니다.
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        // when
        companyDeletedEventConsumer.consume(message, acknowledgment);

        // then
        then(productService).should().deleteAllByCompanyId(companyId);
        then(acknowledgment).should().acknowledge();
    }

    @Test
    @DisplayName("상품 삭제 비즈니스 로직 중 예외가 발생하면 외부로 에러를 전파한다(재시도 핸들러 위임)")
    void consume_deleteProducts_fail_throwException() throws Exception {
        // given
        UUID companyId = UUID.randomUUID();
        CompanyDeletedEvent event = new CompanyDeletedEvent(companyId, UUID.randomUUID(), LocalDateTime.now());
        String message = objectMapper.writeValueAsString(event);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        // 서비스 레이어 장애 강제 발생 설정
        doThrow(new RuntimeException("DB Deadlock 등 시스템 에러"))
                .when(productService).deleteAllByCompanyId(companyId);

        // when & then
        assertThatThrownBy(() -> companyDeletedEventConsumer.consume(message, acknowledgment))
                .isInstanceOf(RuntimeException.class);
    }
}