package com.sparta.logistics.slack.controller;

import com.sparta.logistics.slack.ai.service.AiMessageService;
import com.sparta.logistics.slack.kafka.DeliveryCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TestController {

  private final AiMessageService aiMessageService;

  @GetMapping("/api/test/ai-slack")
  public String testAiAndSlack() {
    log.info("TestController -AI 슬랙 발송 프로세스 시작");

    // 1. 카프카에서 수신할 이벤트 객체 생성
    DeliveryCreatedEvent mockEvent = DeliveryCreatedEvent.builder()
        .eventId(UUID.randomUUID().toString())
        .deliveryId(UUID.randomUUID())
        .orderId(UUID.randomUUID())
        .sourceHubId(UUID.randomUUID())
        .destinationHubId(UUID.randomUUID())
        .companyDeliveryManagerId(UUID.randomUUID())
        .totalDeliveryCount(1)
        .deliveryAddress("부산시 사하구 낙동대로 1번길 1 해산물 월드")
        .totalEstimatedDuration(450) //분
        .createdAt(LocalDateTime.now())
        .build();

    // 2. Gemini API 호출해서 텍스트 생성
    String generatedMessage = aiMessageService.sendDeliveryDeadlineMessage(mockEvent);

    return "AI 슬랙 발송 성공! \n\n [생성된 메시지]\n" + generatedMessage;
  }
}