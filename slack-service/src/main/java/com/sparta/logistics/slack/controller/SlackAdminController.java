package com.sparta.logistics.slack.controller;

import com.sparta.logistics.slack.ai.service.AiMessageService;
import com.sparta.logistics.slack.kafka.DeliveryCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SlackAdminController {

  //private final AiMessageService aiMessageService;
  private final KafkaTemplate<String, Object> kafkaTemplate;

  @GetMapping("/api/v1/admin/slack/manual-send")
  public String manualSendAiSlack() {
    //log.info("SlackAdminController -AI 슬랙 발송 프로세스 시작");
    log.info("SlackAdminController - 카프카 토픽으로 이벤트 강제 발생!");

    // 1. 카프카에서 수신할 이벤트 객체 생성
    DeliveryCreatedEvent mockEvent = DeliveryCreatedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .deliveryId(UUID.randomUUID())
        .orderId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
        .sourceHubId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
        .destinationHubId(UUID.fromString("55555555-5555-5555-5555-555555555555"))
        .companyDeliveryManagerId(UUID.randomUUID())
        .totalDeliveryCount(1)
        .deliveryAddress("부산시 사하구 낙동대로 1번길 1 해산물 월드")
        .totalEstimatedDuration(450) //분
        .createdAt(LocalDateTime.now())
        .build();

    // 2. Gemini API 호출해서 텍스트 생성
    //String generatedMessage = aiMessageService.sendDeliveryDeadlineMessage(mockEvent);
    kafkaTemplate.send("delivery.created", mockEvent);

    //return "관리자 수동 AI 슬랙 발송 성공! \n\n [생성된 메시지]\n" + generatedMessage;
    return "카프카 이벤트 발행 완료! 콘솔 확인";
  }
}