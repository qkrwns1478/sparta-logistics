package com.sparta.logistics.slack.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.slack.dto.request.SlackMessageCreateRequest;
import com.sparta.logistics.slack.dto.request.SlackMessageSearchCondition;
import com.sparta.logistics.slack.dto.request.SlackMessageUpdateRequest;
import com.sparta.logistics.slack.dto.response.SlackMessageResponse;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;
import com.sparta.logistics.slack.service.SlackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/slack-messages")
@RequiredArgsConstructor
public class SlackController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final SlackService slackService;

    @PostMapping
    public ResponseEntity<ApiResponse<SlackMessageResponse>> createSlackMessage(
            @Valid @RequestBody SlackMessageCreateRequest request,
            @RequestHeader(USER_ID_HEADER) UUID userId
    ) {
        SlackMessageResponse response = slackService.createSlackMessage(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("슬랙 메시지가 발송되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SlackMessageResponse>>> getSlackMessages(
            @RequestParam(required = false) String receiverSlackId,
            @RequestParam(required = false) MessageType messageType,
            @RequestParam(required = false) SlackMessageStatus status,
            @RequestParam(required = false) RelatedType relatedType,
            @RequestParam(required = false) UUID relatedId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        SlackMessageSearchCondition condition = new SlackMessageSearchCondition(
                receiverSlackId,
                messageType,
                status,
                relatedType,
                relatedId
        );

        Page<SlackMessageResponse> response = slackService.getSlackMessages(
                condition,
                buildPageable(page, validatePageSize(size), sort)
        );

        return ResponseEntity.ok(ApiResponse.ok("요청이 성공적으로 처리되었습니다.", response));
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<ApiResponse<SlackMessageResponse>> updateSlackMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody SlackMessageUpdateRequest request
    ) {
        SlackMessageResponse response = slackService.updateSlackMessage(messageId, request);
        return ResponseEntity.ok(ApiResponse.ok("슬랙 메시지가 수정되었습니다.", response));
    }

    private int validatePageSize(int size) {
        if (size == 30 || size == 50) {
            return size;
        }
        return 10;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        try {
            String[] parts = sort.split(",");
            String field = parts[0].trim();
            Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            return PageRequest.of(page, size, Sort.by(direction, field));
        } catch (Exception e) {
            return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        }
    }
}
