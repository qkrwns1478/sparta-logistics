package com.sparta.logistics.slack.ai.client;

import com.sparta.logistics.slack.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hub-service", configuration = FeignConfig.class)
public interface HubFeignClient {

  @GetMapping("/api/v1/hubs/{hubId}")
  HubWrapper getHub(@PathVariable("hubId") UUID hubId);

  record HubWrapper(
      HubResponseDto data
  ){}

  record HubResponseDto(
      UUID hubId,
      String name
  ){}
}
