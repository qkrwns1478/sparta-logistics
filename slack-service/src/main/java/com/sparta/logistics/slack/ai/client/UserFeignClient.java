package com.sparta.logistics.slack.ai.client;

import com.sparta.logistics.slack.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "user-service", configuration = FeignConfig.class)
public interface UserFeignClient {

  @GetMapping("/api/v1/users/{userId}")
  UserWrapper getUser(
      @PathVariable("userId") UUID userId,
      @RequestHeader("X-User-Id") String headerUserId,
      @RequestHeader("X-Role") String role);

  record UserWrapper(
      UserResponseDto data
  ){}

  record UserResponseDto(
      String slackId,
      String username
  ){}
}
