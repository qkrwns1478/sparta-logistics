package com.sparta.logistics.slack.ai.service;

import com.sparta.logistics.slack.ai.client.GeminiApiClient;
import com.sparta.logistics.slack.ai.client.HubFeignClient;
import com.sparta.logistics.slack.ai.client.OrderFeignClient;
import com.sparta.logistics.slack.ai.client.UserFeignClient;
import com.sparta.logistics.slack.ai.entity.AiLog;
import com.sparta.logistics.slack.ai.prompt.DeliveryPromptTemplate;
import com.sparta.logistics.slack.ai.repository.AiLogRepository;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.kafka.DeliveryCreatedEvent;
import com.sparta.logistics.slack.sender.SlackApiSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageService {

  private final GeminiApiClient geminiApiClient;
  private final HubFeignClient hubFeignClient;
  private final OrderFeignClient orderFeignClient;
  private final UserFeignClient userFeignClient;
  private final DeliveryPromptTemplate promptTemplate;
  private final AiLogRepository aiLogRepository;
  private final SlackApiSender slackApiSender;

  @Value("${slack.receiver.test-id}")
  private String receiverSlackId;

  public String sendDeliveryDeadlineMessage(DeliveryCreatedEvent event) {

    String fullPrompt = "";

    try {
      // 1. 허브 데이터 조회 (출발지/도착지 허브 이름 가져오기) 실패 시 기본값
      Map<UUID, String> hubNameMap = new HashMap<>();
      try {
        HubFeignClient.HubResponseDto sourceHub = hubFeignClient.getHub(event.getSourceHubId()).data();
        HubFeignClient.HubResponseDto destHub = hubFeignClient.getHub(event.getDestinationHubId()).data();

        hubNameMap.put(sourceHub.hubId(), sourceHub.name());
        hubNameMap.put(destHub.hubId(), destHub.name());
      } catch (Exception e) {
        log.error("허브 서버 통신 실패로 출발/도착 허브 정보를 알 수 없습니다!");
        log.error("요청한 SourceHubId:{}, DestHubId: {}", event.getSourceHubId(), event.getDestinationHubId());
        log.error("실패 원인: {}",e.getMessage());

        throw new RuntimeException("필수 배송 정보(출발/도착 허브 이름) 누락으로 기사님 알림 발송이 취소되었습니다.");
      }

      //2. 주문 서비스에서 데이터 가져오기
      OrderFeignClient.OrderResponseDto orderData;
      try {
        orderData = orderFeignClient.getOrder(event.getOrderId()).data();
        //필수 데이터가 비어있다면 강제로 에러를 던져 Fallback으로 넘김
        if (orderData == null || orderData.orderItems() == null || orderData.orderItems().isEmpty()) {
          throw new IllegalArgumentException("주문 상품 정보가 비어있습니다.");
        }
      } catch (Exception e) {
        log.warn("필수 데이터(주문 정보) 누락 발생! AI 요청을 취소하고 Fallback");
        log.error("주문 통신 실패 원인: {}", e.getMessage());
        return executeFallbackProcess(event);
      }

      //3. 정상인 경우, 이벤트 데이터를 프롬프트 가공 (totalDeliveryCount 제외)
      String eventData = convertEventToPromptData(event, hubNameMap, orderData);
      //3-1. 프롬프트 템플릿
      fullPrompt = promptTemplate.buildDeadlinePrompt(eventData);
      //3-2. Gemini API 호출
      GeminiApiClient.AiMessageResult aiResult = geminiApiClient.generateDeliveryDeadlineMessage(fullPrompt);

      //슬랙과 AI로그를 연결할 공통 ID 생성
      UUID slackMessageId = UUID.randomUUID();

      //4. AI 로그 저장 (성공)
      aiLogRepository.save(
          AiLog.builder()
              .slackMessageId(slackMessageId)
              .orderId(event.getOrderId())
              .requestType(AiLog.AiRequestType.DEADLINE)
              .requestContent(fullPrompt)
              .responseContent(aiResult.message())
              .status(AiLog.AiLogStatus.SUCCESS)
              .build()
      );

      //5. 슬랙 타겟 ID
      String targetSlackId = receiverSlackId;

      if (orderData.requesterUserId() != null) {
        try {
          UserFeignClient.UserResponseDto userData =
              userFeignClient.getUser(UUID.fromString(orderData.requesterUserId())).data();

          if (userData != null && userData.slackId() != null && !userData.slackId().isEmpty()) {
            targetSlackId = userData.slackId();
            log.info("유저 서버 통신 성공! 슬랙 ID 수신:{}", targetSlackId);
          }
        } catch (Exception e) {
          log.warn("유저 통신 실패 - 기본 슬랙 ID로 대체합니다. 원인: {}", e.getMessage());
        }
      }

      //6. 최종 슬랙 발송
      slackApiSender.send(
          slackMessageId,
          targetSlackId,
          aiResult.message(),
          MessageType.AI_DELIVERY_DEADLINE,
          RelatedType.DELIVERY,
          event.getDeliveryId()
      );

      return aiResult.message();

    } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      //AI 서버가 터졌을 때 (503 등) -> 카프카가 재시도하도록 예외를 던짐
      log.error("Gemini API 서버 오류 발생! 카프카 재시도를 위해 예외를 던집니다.");
      throw e;
    } catch (Exception e) {
      //7. AI 로그 저장 (실패) DB에 저장
      log.error("AI 배송 마감 메시지 생성 중 예외 발생: ", e);
      aiLogRepository.save(
          AiLog.builder()
              .orderId(event.getOrderId())
              .requestType(AiLog.AiRequestType.DEADLINE)
              .requestContent(fullPrompt.isEmpty() ? "데이터 조합 실패" : fullPrompt)
              .responseContent("AI 메시지 생성 실패" + e.getMessage())
              .status(AiLog.AiLogStatus.FAILED)
              .build()
      );
      throw e;
    }
  }

  private String executeFallbackProcess(DeliveryCreatedEvent event) {
    //총 소요 시간에 '2시간(120분)'의 안전 버퍼를 더합니다.
    int fallbackDurationMin = event.getTotalEstimatedDuration() + 120;
    double fallbackHours = fallbackDurationMin / 60.0;

    String fallbackMessage = """
        [시스템 자동 계산 안내]
        주문 번호: %s
        "일부 데이터 지연으로 인해 AI 계산이 생략되었습니다.
        시스템 기본 설정에 따른 최종 발송 시한은 [배송 시작 시각 기준 + 약 %.1f시간] 입니다.
        담당자께서는 해당 시간 내에 처리를 부탁드립니다.
        """.formatted(event.getOrderId(), fallbackHours);

    //Fallback ID
    UUID fallbackSlackMsgId = UUID.randomUUID();

    //Fallback 메시지를 슬랙으로 발송 (배송 흐름 정상화)
    slackApiSender.send(
        UUID.randomUUID(), receiverSlackId, fallbackMessage,
        MessageType.AI_DELIVERY_DEADLINE, RelatedType.DELIVERY, event.getDeliveryId()
    );

    //Fallback 로그 저장
    aiLogRepository.save(
        AiLog.builder()
            .slackMessageId(fallbackSlackMsgId)
            .orderId(event.getOrderId())
            .requestType(AiLog.AiRequestType.DEADLINE)
            .responseContent("주문 도메인 데이터 누락으로 AI 프롬프트 생성 생략")
            .responseContent(fallbackMessage)
            .status(AiLog.AiLogStatus.FAILED)
            .build()
    );

    log.info("Fallback 슬랙 메시지 발송 및 DB 저장 완료: {}", event.getDeliveryId());
    return fallbackMessage;
  }

  private String convertEventToPromptData(
      DeliveryCreatedEvent event,
      Map<UUID, String> hubNameMap,
      OrderFeignClient.OrderResponseDto orderData) {

    double durationHours = event.getTotalEstimatedDuration() / 60.0;
    String customerRequest = orderData.customerRequest() != null ? orderData.customerRequest() : "없음";
    String productInfo = orderData.orderItems().stream()
        .map(item -> item.productName() + " " + item.quantity() + "개")
        .collect(Collectors.joining(", "));

    return
        """
            Order: %s
            StartTime: %s
            Product: %s
            Due: %s
            RoutePath: %s -> %s -> %s
            TotalTransitTime: %d분 (약 %.1f시간)
            Manager: %s
            """.formatted(
            event.getOrderId(),
            event.getCreatedAt(),
            productInfo,
            customerRequest,
            hubNameMap.getOrDefault(event.getSourceHubId(), "알 수 없는 출발지"),
            hubNameMap.getOrDefault(event.getDestinationHubId(), "알 수 없는 도착지"),
            event.getDeliveryAddress(),
            event.getTotalEstimatedDuration(),
            durationHours,
            event.getCompanyDeliveryManagerId()
        );
  }
}

