package com.sparta.logistics.user.client.support;

import com.sparta.logistics.user.client.HubServiceClient;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * PostgreSQL · Redis 등 없이 Feign 클라이언트만 검증하기 위한 최소 부트 설정.
 */
@SpringBootApplication(scanBasePackages = {}, exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
@EnableFeignClients(basePackageClasses = HubServiceClient.class)
@ComponentScan(basePackages = {
        "com.sparta.logistics.user.client",
        "com.sparta.logistics.user.application.validator"
})
public class FeignClientsTestBootApp {
}

