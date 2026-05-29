package com.sparta.logistics.slack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaErrorHandlerConfig {

  @Bean
  public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate){
    
    //1. DLQ 전송 설정
    //실패한 메시지는 원래 토픽 이름 뒤에 '.DLT'가 붙은 토픽으로 자동 이동
    DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

    //2. 지수 백오프 (Exponential Backoff) 설정
    //1초(1000ms) 대기 후 1차 재시도 -> 2배(2초) 대기 후 2차 -> 4초 대기 후 3차 시도
    ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
    backOff.setMaxAttempts(3);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

    //3. 예외 분류
    // 실패가 확실한 에러는 DLQ로 바로 처리
    errorHandler.addNotRetryableExceptions(
        IllegalArgumentException.class,
        NullPointerException.class,
        ClassCastException.class //직렬화/역직렬화 타입 에러
    );
    return errorHandler;
  }
}
