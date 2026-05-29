package com.sparta.logistics.slack.ai.entity;

import com.sparta.logistics.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "p_ai_log")
public class AiLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "slack_message_id")
  private UUID slackMessageId;

  @Column(name = "order_id")
  private UUID orderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "Request_type", length = 50)
  private AiRequestType requestType;

  @Column(name = "request_content", columnDefinition = "TEXT")
  private String requestContent;

  @Column(name ="response_content", columnDefinition = "TEXT")
  private String responseContent;

  @Column(name ="system_prompt", columnDefinition = "TEXT")
  private String systemPrompt;

  @Column(name ="final_deadline_at")
  private LocalDateTime finalDeadLineAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 50)
  private AiLogStatus status;

  public enum AiRequestType{
    DEADLINE, ROUTE
  }

  public enum AiLogStatus{
    PENDING, SUCCESS, RETRY, FAILED
  }


}
