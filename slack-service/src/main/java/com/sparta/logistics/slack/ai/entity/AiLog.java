package com.sparta.logistics.slack.ai.entity;

import com.sparta.logistics.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "p_ai_logs")
public class AiLog extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String promptData;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String responseMessage;

  @Column(nullable = false)
  private Integer totalTokens;

  @Column(nullable = false)
  private  Boolean success;

  private String errorMessage;
}
