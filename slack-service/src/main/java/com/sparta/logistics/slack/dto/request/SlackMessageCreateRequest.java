package com.sparta.logistics.slack.dto.request;

import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SlackMessageCreateRequest(
        @NotBlank(message = "수신자 슬랙 ID는 필수입니다.")
        @Size(max = 100, message = "수신자 슬랙 ID는 최대 100자입니다.")
        String receiverSlackId,

        @NotBlank(message = "메시지는 필수입니다.")
        String message,

        @NotNull(message = "메시지 타입은 필수입니다.")
        MessageType messageType,

        RelatedType relatedType,
        UUID relatedId
) {
}
