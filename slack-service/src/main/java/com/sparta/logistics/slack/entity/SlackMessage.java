package com.sparta.logistics.slack.entity;

import com.sparta.logistics.common.domain.BaseEntity;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;
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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "p_slack_message")
public class SlackMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "receiver_slack_id", nullable = false, length = 100)
    private String receiverSlackId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private MessageType messageType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlackMessageStatus status = SlackMessageStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type", length = 30)
    private RelatedType relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "sender_id")
    private UUID senderId;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "slack_ts", length = 50)
    private String slackTs;

    @Column(name = "slack_channel_id", length = 50)
    private String slackChannelId;

    public void markAsSent(String slackTs, String slackChannelId) {
        this.status = SlackMessageStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.slackTs = slackTs;
        this.slackChannelId = slackChannelId;
    }

    public void markAsFailed() {
        this.status = SlackMessageStatus.FAILED;
    }

    public void increaseRetryCount() {
        this.retryCount++;
    }

    public void updateMessage(String newMessage) {
        if (this.status == SlackMessageStatus.FAILED) {
            throw new IllegalStateException("발송 실패한 메시지는 수정할 수 없습니다.");
        }
        this.message = newMessage;
    }
}
