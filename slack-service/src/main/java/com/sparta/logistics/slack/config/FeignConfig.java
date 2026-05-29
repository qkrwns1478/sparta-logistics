package com.sparta.logistics.slack.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

  @Bean
  public RequestInterceptor requestInterceptor() {
    return requestTemplate -> {
      requestTemplate.header("Content-Type", "application/json");
      requestTemplate.header("X-User-Id", "550e8400-e29b-41d4-a716-446655440000");
      requestTemplate.header("X-User-Role", "MASTER");
    };
  }
}
