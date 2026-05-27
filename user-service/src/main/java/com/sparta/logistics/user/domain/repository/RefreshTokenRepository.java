package com.sparta.logistics.user.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final long TTL_DAYS = 5;
    private final RedisTemplate<String, String> redisTemplate;

    // 저장 (key: refresh:{userId}, value: refreshToken, TTL: 5일)
    public void save(String userId, String refreshToken) {
        redisTemplate.opsForValue()
                .set(key(userId), refreshToken, TTL_DAYS, TimeUnit.DAYS);
    }

    // 조회
    public Optional<String> find(String userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    // 삭제
    public void delete(String userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(String userId) {
        return "refresh:" + userId;
    }
}