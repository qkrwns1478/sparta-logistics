package com.sparta.logistics.slack.sender;

public record FakeSlackSendResult(
        String slackTs,
        String slackChannelId
) {
}
