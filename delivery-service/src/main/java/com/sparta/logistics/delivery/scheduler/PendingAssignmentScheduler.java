package com.sparta.logistics.delivery.scheduler;

import com.sparta.logistics.delivery.repository.DeliveryRepository;
import com.sparta.logistics.delivery.repository.DeliveryRouteRepository;
import com.sparta.logistics.delivery.service.DeliveryAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 미배차 건 자동 재시도 스케줄러.
 *
 * <p>배차 시점에 가용 담당자가 없어 hubDeliveryManagerId = null인 route가 남은 경우
 * 5분 주기로 재배차를 시도한다.
 *
 * <p>2시간 이상 미배차 상태가 지속되면 WARN 로그를 남겨 운영자가 확인할 수 있도록 한다.
 * 자동 배송 취소는 하지 않는다 — OrderService 보상 트랜잭션 연쇄 발생 위험.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingAssignmentScheduler {

    private final DeliveryRouteRepository routeRepository;
    private final DeliveryAssignmentService assignmentService;
    private final DeliveryRepository deliveryRepository;

    @Scheduled(fixedDelay = 300_000)
    public void retryPendingAssignments() {
        List<UUID> pendingIds = routeRepository.findDeliveryIdsWithUnassignedRoutes();
        if (pendingIds.isEmpty()) return;

        log.info("[스케줄러] 미배차 건 재시도 — {}건", pendingIds.size());
        for (UUID deliveryId : pendingIds) {
            deliveryRepository.findById(deliveryId).ifPresent(d -> {
                if (d.getCreatedAt().isBefore(LocalDateTime.now().minusHours(2))) {
                    log.warn("[스케줄러] 2시간 이상 미배차 — deliveryId={}, 운영자 확인 필요", deliveryId);
                }
            });
            try {
                assignmentService.assignManagersForSystem(deliveryId);
            } catch (Exception e) {
                log.warn("[스케줄러] 재시도 실패 — deliveryId={}", deliveryId, e);
            }
        }
    }
}
