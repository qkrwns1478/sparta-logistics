package com.sparta.logistics.slack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubFeignClient {

  @GetMapping("/api/v1/hubs/batch")
  List<HubBatchResponseDto> getHubsBatch(@RequestParam("hubIds") List<UUID> hubIds);

  record HubBatchResponseDto(
      UUID hubId,
      String name
  ){}
}
