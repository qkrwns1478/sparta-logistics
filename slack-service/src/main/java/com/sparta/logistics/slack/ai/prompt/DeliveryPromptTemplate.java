package com.sparta.logistics.slack.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class DeliveryPromptTemplate {
  public String buildDeadlinePrompt(String promptData){
    return """ 
      당신은 물류 배송 시한을 계산하는 전문가입니다. 아래 주어진 [배송 데이터]를 분석하여 담당자가 납기 요청을 준수할 수 있도록 슬랙 메시지를 생성하세요.
      
      [계산 규칙]
      - 배송 상담자 근무 시간 : 09:00 ~ 18:00
      - '기준 시각(배송 시작)'부터 '총 예상 소요 시간'이 경과할 때, 근무시간(하루 9시간 근무)의 제약조건을 논리적으로 누적 반영하여 최종 배송이 완료되거나 츌벌햐여 허눈 '최종 발송 마감 시한'을 정확히 계산하세요.
      - 서론과 결론 없이 아래 [메시지 출력 포맷]과 100% 동일하게 작성하세요.
      
      [메시지 출력 포맷]
      주문 번호 : {Order}
      배송 시작 시각 : {StartTime}
      상품 정보 : {Product}
      요청 사항 : {Due}
      배송 경로 : {RoutePath}
      총 소요 시간 : {TotalTransitTime}
      배송 담당자 슬랙 ID : {Manager}
      
      도출된 최종 발송 시한은 {계산된_월_일} {오전/오후} 입니다.
      
      [배송 데이터]
      """ + promptData;

  }
}
