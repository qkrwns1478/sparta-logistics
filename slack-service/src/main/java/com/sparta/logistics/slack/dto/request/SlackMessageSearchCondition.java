package com.sparta.logistics.slack.dto.request;

import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;

import java.util.UUID;

public record SlackMessageSearchCondition(
        String receiverSlackId,
        MessageType messageType,
        SlackMessageStatus status,
        RelatedType relatedType,
        UUID relatedId
) {
}
