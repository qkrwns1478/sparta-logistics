package com.sparta.logistics.gateway.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import javax.crypto.SecretKey;
import java.util.Base64;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder(@Value("${jwt.secret-key}") String secretKeyBase64) {
        byte[] secretBytes = Base64.getDecoder().decode(secretKeyBase64);
        SecretKey secretKey = Keys.hmacShaKeyFor(secretBytes);
        return NimbusReactiveJwtDecoder.withSecretKey(secretKey).build();
    }
}

