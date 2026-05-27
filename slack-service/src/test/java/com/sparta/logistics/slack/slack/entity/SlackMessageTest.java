package com.sparta.logistics.slack.slack.entity;

import com.sparta.logistics.slack.entity.SlackMessage;
import com.sparta.logistics.slack.enums.SlackMessageStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SlackMessageTest {

  @Test
  @DisplayName("PENDING 상태일 때는 메시지를 수정할 수 있다")
  void updateMessageSuccess(){
    SlackMessage message = SlackMessage.builder()
        .message("초기 메시지")
        .status(SlackMessageStatus.PENDING)
        .build();

    message.updateMessage("수정된 메시지");

    assertThat(message.getMessage()).isEqualTo("수정된 메시지");
  }

  @Test
  @DisplayName("SENT 상태일 때는 메시지 수정 시 예외 발생")
  void updateMessageFailWhenSent(){
    SlackMessage message = SlackMessage.builder()
        .message("초기 메시지")
        .status(SlackMessageStatus.SENT)
        .build();

    assertThatThrownBy(() -> message.updateMessage("수정된 메시지"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("발송 실패했거나 이미 발송된 메시지는 수정할 수 없습니다.");
  }

  @Test
  @DisplayName("FAILED 상태일 때는 메시지 수정 시 예외 발생")
  void updateMessageFailWhenFailed(){
    SlackMessage message = SlackMessage.builder()
        .message("초기 메시지")
        .status(SlackMessageStatus.FAILED)
        .build();

    assertThatThrownBy(() -> message.updateMessage("수정된 메시지"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("발송 실패했거나 이미 발송된 메시지는 수정할 수 없습니다.");
  }

  @Test
  @DisplayName("markAsSent 호출 시 상태가 SENT로 변경되고 슬랙 발송 정보 세팅")
  void markAsSentSuccess(){
    SlackMessage message = SlackMessage.builder()
        .status(SlackMessageStatus.PENDING)
        .build();

    message.markAsSent("slack-ts-12345", "slack-channel-abc");

    assertThat(message.getStatus()).isEqualTo(SlackMessageStatus.SENT);
    assertThat(message.getSlackTs()).isEqualTo("slack-ts-12345");
    assertThat(message.getSlackChannelId()).isEqualTo("slack-channel-abc");
    assertThat(message.getSentAt()).isNotNull(); //발송 시간 기록 확인
  }

  @Test
  @DisplayName("markAsFailed 호출 시 상태가 FAILED로 변경됨")
  void markAsFailedSuccess(){
    SlackMessage message = SlackMessage.builder()
        .status(SlackMessageStatus.PENDING)
        .build();

    message.markAsFailed();

    assertThat(message.getStatus()).isEqualTo(SlackMessageStatus.FAILED);
  }
}
