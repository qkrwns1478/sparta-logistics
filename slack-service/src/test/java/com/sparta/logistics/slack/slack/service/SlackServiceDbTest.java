package com.sparta.logistics.slack.slack.service;

import com.sparta.logistics.slack.dto.request.SlackMessageCreateRequest;
import com.sparta.logistics.slack.dto.response.SlackMessageResponse;
import com.sparta.logistics.slack.entity.SlackMessage;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;
import com.sparta.logistics.slack.repository.SlackMessageRepository;
import com.sparta.logistics.slack.sender.FakeSlackSender;
import com.sparta.logistics.slack.service.SlackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.cloud.config.enabled=false",
    "eureka.client.enabled=false",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.datasource.url=jdbc:postgresql://localhost:5432/${POSTGRES_DB:logistics}?currentSchema=schema_slack",
    "spring.datasource.username=${POSTGRES_USER}",
    "spring.datasource.password=${POSTGRES_PASSWORD}"
})
public class SlackServiceDbTest {

  @Autowired
  private SlackService slackService;

  @Autowired
  private SlackMessageRepository slackMessageRepository;

  @MockitoBean
  private FakeSlackSender fakeSlackSender;


  @Test
  @Commit
  @DisplayName("발송 에러 시 FAILED 상태로 실제 DB에 이력이 저장되는지 확인")
  void checkDbLogWhenApiFails(){

    SlackMessageCreateRequest request = new SlackMessageCreateRequest(
        "U12345",
        "DB 확인용 실패 테스트 메시지",
        MessageType.MANUAL,
        null,
        null
    );

    given(fakeSlackSender.send(any(), any(), any(), any(), any(), any()))
        .willThrow(new RuntimeException("강제 실패"));

    SlackMessageResponse response = slackService.createSlackMessage(request, UUID.randomUUID());
    assertThat(response.status()).isEqualTo(SlackMessageStatus.FAILED);

    SlackMessage saved = slackMessageRepository.findById(response.slackMessageId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(SlackMessageStatus.FAILED);
  }
}
