package com.sparta.logistics.user.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "hub-service")
public interface HubServiceClient {

    @GetMapping("/api/v1/hubs/{hubId}/exists")
    void checkHubExists(@PathVariable("hubId") UUID hubId);
}
