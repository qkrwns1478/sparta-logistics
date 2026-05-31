package com.sparta.logistics.slack.ai.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.logistics.slack.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "order-service", configuration = FeignConfig.class)
public interface OrderFeignClient {

  @GetMapping("api/v1/orders/{orderId}")
  OrderWrapper getOrder(
      @PathVariable("orderId") UUID orderId,
      @RequestHeader("X-User-Id") String userId,
      @RequestHeader("X-Role") String role);

  record OrderWrapper(
      OrderResponseDto data
  ){}

  record OrderResponseDto(
      List<OrderItemDto> orderItems,

      @JsonProperty("requestMemo")
      String customerRequest,

      @JsonProperty("requesterUserId")
      String requesterUserId //수신자 슬랙 ID
  ){}

  record OrderItemDto(
      String productName,
      int quantity
  ){}
}
