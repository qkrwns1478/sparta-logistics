package com.sparta.logistics.company.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.common.kafka.event.CompanyDeleteRollbackEvent;
import com.sparta.logistics.company.repository.CompanyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CompanyDeleteRollbackConsumerTest {

    @Mock private CompanyRepository companyRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper(); // 실제 JSON 파싱 검증을 위해 Spy 사용
    @InjectMocks private CompanyDeleteRollbackConsumer rollbackConsumer;

    @Test
    @DisplayName("보상 이벤트를 수신하면 대상을 복구(restoreById)하고 ACK를 처리한다")
    void consume_rollback_success() throws Exception {
        // given
        UUID companyId = UUID.randomUUID();
        CompanyDeleteRollbackEvent event = new CompanyDeleteRollbackEvent(companyId, "테스트용 에러 롤백");
        String message = objectMapper.writeValueAsString(event);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        given(companyRepository.restoreById(companyId)).willReturn(1); // 1건 업데이트 성공 빈 주입

        // when
        rollbackConsumer.consume(message, acknowledgment);

        // then
        then(companyRepository).should().restoreById(companyId); // 복구 쿼리 호출 검증
        then(acknowledgment).should().acknowledge(); // 카프카 수신 확인(ACK) 호출 검증
    }
}