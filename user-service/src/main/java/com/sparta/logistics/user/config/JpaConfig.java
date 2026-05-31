package com.sparta.logistics.user.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@Configuration
@EntityScan({"com.sparta.logistics.user", "com.sparta.logistics.common"})
@EnableJpaRepositories({"com.sparta.logistics.user", "com.sparta.logistics.common"})
public class JpaConfig {
}
