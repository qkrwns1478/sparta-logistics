package com.sparta.logistics.slack.kafka;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DeliveryCreatedEvent {
  private String eventId;
  private UUID deliveryId;
  private UUID orderId;
  private UUID sourceHubId;
  private UUID destinationHubId;
  private UUID companyDeliveryManagerId;
  private Integer totalDeliveryCount;
  private String deliveryAddress; //최종 배송지 주소
  private int totalEstimatedDuration; // 총 소요시간
  private LocalDateTime createdAt; //소요 시간 계산 기준

}
