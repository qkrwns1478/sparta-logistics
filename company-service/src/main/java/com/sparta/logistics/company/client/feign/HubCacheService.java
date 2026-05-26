package com.sparta.logistics.company.client.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.logistics.company.client.model.HubResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 허브 정보(이름) 캐싱
 * - 허브 정보는 변경 빈도가 낮아 캐싱 적용
 * - 17개 허브 고정이므로 캐시 부담 없음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HubCacheService {

    private final HubFeignClient hubFeignClient;
    private final RedisTemplate<String, String> redisTemplate;  // String 직렬화
    private final ObjectMapper objectMapper;

    private static final String HUB_CACHE_PREFIX = "hubs::";
    private static final Duration TTL = Duration.ofMinutes(10);

    // @Cacheable 사용 시 타입 캐스팅 문제로 인해 RedisTemplate 기반 수동 캐시 처리
    public HubResponse getHub(UUID hubId) {
        String key = HUB_CACHE_PREFIX + hubId;
        String cached = redisTemplate.opsForValue().get(key);

        if (cached != null) {
            try {
                return objectMapper.readValue(cached, HubResponse.class);
            } catch (Exception e) {
                // 역직렬화 실패 시 캐시 무효화 후 재조회
                redisTemplate.delete(key);
            }
        }

        HubResponse response = hubFeignClient.getHub(hubId);
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(response), TTL);
        } catch (Exception e) {
            log.warn("[HubCacheService] 캐시 저장 실패. hubId={}", hubId);
        }
        return response;
    }

    public Map<UUID, String> getHubNameMap(List<UUID> hubIds) {
        return hubFeignClient.getHubsByIds(hubIds)
                .stream()
                .collect(Collectors.toMap(HubResponse::hubId, HubResponse::name));
    }
}
