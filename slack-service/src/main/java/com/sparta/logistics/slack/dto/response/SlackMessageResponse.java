package com.sparta.logistics.slack.dto.response;

import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record SlackMessageResponse(
        UUID slackMessageId,
        String receiverSlackId,
        String message,
        MessageType messageType,
        SlackMessageStatus status,
        RelatedType relatedType,
        UUID relatedId,
        UUID senderId,
        int retryCount,
        LocalDateTime sentAt,
        String slackTs,
        String slackChannelId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
