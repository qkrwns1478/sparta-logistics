package com.sparta.logistics.delivery.repository;

import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.RouteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryRouteRepository extends JpaRepository<DeliveryRouteEntity, UUID> {

    List<DeliveryRouteEntity> findAllByDelivery_IdOrderBySequenceAsc(UUID deliveryId);

    boolean existsByHubDeliveryManagerIdAndStatusIn(UUID managerId, List<RouteStatus> statuses);

    // 6번: 허브 담당자 권한 체크용 — 해당 delivery의 특정 구간 담당자 여부 확인
    boolean existsByDelivery_IdAndHubDeliveryManagerId(UUID deliveryId, UUID hubDeliveryManagerId);

    // 8번: 완료·취소 배송 및 비대기 경로 제외하여 스케줄러 불필요 재처리 방지
    @Query("SELECT DISTINCT r.delivery.id FROM DeliveryRouteEntity r " +
           "WHERE r.hubDeliveryManagerId IS NULL " +
           "AND r.status = com.sparta.logistics.delivery.entity.enums.RouteStatus.WAITING " +
           "AND r.delivery.deletedAt IS NULL " +
           "AND r.delivery.status NOT IN (" +
           "  com.sparta.logistics.delivery.entity.enums.DeliveryStatus.COMPLETED," +
           "  com.sparta.logistics.delivery.entity.enums.DeliveryStatus.CANCELLED)")
    List<UUID> findDeliveryIdsWithUnassignedRoutes();
}
