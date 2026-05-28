package com.sparta.logistics.slack.ai.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiApiClient {

  private final WebClient webClient;

  @Value("${gemini.api.key}")
  private String geminiApiKey;

  public record AiMessageResult(String message, int totalTokens){}

  @SuppressWarnings("unchecked")
  public AiMessageResult generateDeliveryDeadlineMessage(String fullPrompt){
    //1. 프롬프트 템플릿 구성
    Map<String, Object> requestBody = new HashMap<>();
    Map<String, Object> parts = new HashMap<>();
    parts.put("text", fullPrompt);
    Map<String, Object> contents = new HashMap<>();
    contents.put("parts", List.of(parts));
    requestBody.put("contents", List.of(contents));

    //2. Gemini API 요청 스펙에 맞게 JSON Body 구성
    try {
      //3. WebClient로 비동기/동기 API 호출
      Map<String, Object> response = webClient.post()
          .uri(uriBuilder -> uriBuilder.queryParam("key", geminiApiKey).build())
          .bodyValue(requestBody)
          .retrieve()
          .bodyToMono(Map.class)
          .block(); //동기(block)처리, 필요시 Mono 비동기로 유지

      //4.응답 JSON에서 텍스트 파싱 및 토큰 추출
      String aiMessage = parseGeminiResponse(response);
      int tokenCount = parseTokenCount(response);
      return new AiMessageResult(aiMessage, tokenCount);

    }catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
      log.error("Gemini API 거절 상세 이유: {}", e.getResponseBodyAsString());
      throw new RuntimeException("AI 메시지 생성 실패"+ e.getResponseBodyAsString(), e);
    } catch (Exception e){
      log.error("Gemini API 호출 중 에러 발생" , e);
      throw new RuntimeException("AI 메시지 생성 실패", e);
    }
  }

  //Gemini API의 JSON 응답 구조에서 실제 답변 텍스트만 추출하는 메서드
  @SuppressWarnings("unchecked")
  private String parseGeminiResponse(Map<String, Object> response){
    try{
      List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
      Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
      List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
      return (String) parts.get(0).get("text");
    } catch (Exception e) {
      log.error("Gemini 응답 파싱 실패: {}", response);
      return "배송 정보 알림 생성을 실패했습니다. 기본 메시지로 대체합니다.";
    }
  }

  @SuppressWarnings("unchecked")
  private int parseTokenCount(Map<String, Object> response){
    try{
      Map<String, Object> usageMetadata = (Map<String, Object>) response.get("usageMetadata");
      if (usageMetadata != null && usageMetadata.get("totalTokenCount") != null) {
        return ((Number) usageMetadata.get("totalTokenCount")).intValue();
      }
    } catch (Exception e) {
        log.warn("Gemini 토큰 사용량 파싱 실패 (기본값 0 처리");
    }
    return 0;
  }

}
