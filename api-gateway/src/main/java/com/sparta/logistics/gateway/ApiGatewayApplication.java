package com.sparta.logistics.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import jakarta.annotation.PostConstruct; // 추가
import reactor.core.publisher.Hooks;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableDiscoveryClient
public class ApiGatewayApplication {

	@PostConstruct
	public void init() {
		// Reactor의 Context를 ThreadLocal(MDC 로그 등)로 자동 전파하도록 설정
		Hooks.enableAutomaticContextPropagation();
	}

	public static void main(String[] args) {

		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
