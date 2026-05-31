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
      requestTemplate.header("X-User-Id", "44444444-4444-4444-4444-444444444444");
      requestTemplate.header("X-User-Role", "MASTER");
    };
  }
}
