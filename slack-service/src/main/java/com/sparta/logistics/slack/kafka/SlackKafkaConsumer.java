package com.sparta.logistics.slack.kafka;

import com.sparta.logistics.slack.ai.service.AiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackKafkaConsumer {

  private final AiMessageService aiMessageService;

  @KafkaListener(topics = "delivery.created", groupId = "slack-service-group")
  public void consumeDeliveryCreated(DeliveryCreatedEvent event) {
    log.info("카프카로부터 배송 생성 이벤트 수신 완료: {}", event.getDeliveryId());

    try {
      //1. 수신한 이벤트 던져서 -> 주문/허브 데이터 긁어오고 -> 프롬프트 조합 -> AI 호출 -> 슬랙 발송 -> DB 저장
      String aiMessage = aiMessageService.sendDeliveryDeadlineMessage(event);

      log.info("[Kafka] AI 마감 시한 파이프라인 처리 및 슬랙 발송 완료! \n{}", aiMessage);

    } catch (Exception e) {
      log.error("배송 이벤트 처리 중 에러 발생", e);
      //필요 시 재시도 로직이나 예외 로그 저장
    }
  }
}
