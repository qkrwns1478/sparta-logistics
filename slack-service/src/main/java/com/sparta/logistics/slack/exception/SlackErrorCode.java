package com.sparta.logistics.slack.exception;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SlackErrorCode implements ErrorCode {

    SLACK_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "SLACK_001", "슬랙 메시지를 찾을 수 없습니다."),
    SLACK_MESSAGE_NOT_UPDATABLE(HttpStatus.BAD_REQUEST, "SLACK_002", "수정할 수 없는 슬랙 메시지입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
