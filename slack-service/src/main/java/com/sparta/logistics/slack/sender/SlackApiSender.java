package com.sparta.logistics.slack.sender;

import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Primary
@Component
public class SlackApiSender implements SlackSender{

  @Value("${slack.bot.token}")
  private String token;

  @Override
  public SlackSendResult send(UUID messageId, String receiverSlackId, String message, MessageType messageType, RelatedType relatedType, UUID relatedId){

    try{
      Slack slack = Slack.getInstance();
      MethodsClient methods = slack.methods(token);

      String channelId = receiverSlackId;
      if(receiverSlackId != null && receiverSlackId.startsWith("U")) {
        var openResponse = methods.conversationsOpen(r -> r.users(List.of(receiverSlackId)));
        if (openResponse.isOk()) {
          channelId = openResponse.getChannel().getId();
        } else {
          log.error("DM 오픈 실패: {}", openResponse.getError());
          throw new RuntimeException("DM 오픈 불가: " + openResponse.getError());
        }
      } else {
        log.info("채널 발송: {}", receiverSlackId);
      }



      ChatPostMessageRequest request = ChatPostMessageRequest.builder()
          .channel(channelId)
          .text(message)
          .build();

      ChatPostMessageResponse response = methods.chatPostMessage(request);

      if (response.isOk()){
        log.info("슬랙 발송 성공! 채널: {}", response.getChannel());
        return new SlackSendResult(response.getTs(), response.getChannel());
      } else {
        log.error("슬랙 발송 실패! 통신은 됐으나 슬랙에서 거절함. 에러 코드: {}", response.getError());
        throw new RuntimeException("슬랙 메시지 발송 실패: " + response.getError());
      }
    } catch (Exception e){
      log.error("슬랙 API 통신 중 예외 발생: ", e);
      throw new RuntimeException("슬랙 API 통신 중 서버 에러 발생", e);
    }
  }
}
