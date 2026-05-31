package com.sparta.logistics.order.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * common 모듈의 OutboxEvent 엔티티와 OutboxEventRepository를 JPA 스캔 대상에 포함
 * @SpringBootApplication 클래스에 직접 붙이면 @WebMvcTest 슬라이스가 깨지므로 별도 Config로 분리
 **/
@Configuration
@EntityScan({"com.sparta.logistics.order", "com.sparta.logistics.common"})
@EnableJpaRepositories({"com.sparta.logistics.order", "com.sparta.logistics.common"})
public class JpaConfig {
}
