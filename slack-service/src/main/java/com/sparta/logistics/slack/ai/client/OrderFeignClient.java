package com.sparta.logistics.slack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "order-service")
public interface OrderFeignClient {

  @GetMapping("api/v1/orders/{orderId}")
  OrderResponseDto getOrder(@PathVariable("orderId") UUID orderId);

  record OrderResponseDto(
      List<OrderItemDto> orderItems,
      String customerRequest,
      String customerSlackId //수신자 슬랙 ID
  ){}

  record OrderItemDto(
      String productName,
      int quantity
  ){}
}
