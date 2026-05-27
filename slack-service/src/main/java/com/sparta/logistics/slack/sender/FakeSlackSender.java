package com.sparta.logistics.slack.sender;

import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class FakeSlackSender {

    public FakeSlackSendResult send(
            UUID slackMessageId,
            String receiverSlackId,
            String message,
            MessageType messageType,
            RelatedType relatedType,
            UUID relatedId
    ) {
        log.info("[FakeSlackSender] receiverSlackId={}, messageType={}, relatedType={}, relatedId={}, message={}",
                receiverSlackId,
                messageType,
                relatedType,
                relatedId,
                message);

        return new FakeSlackSendResult(
                "fake-ts-" + slackMessageId,
                "fake-channel"
        );
    }
}
