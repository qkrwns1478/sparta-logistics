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

    @Query("SELECT DISTINCT r.delivery.id FROM DeliveryRouteEntity r " +
           "WHERE r.hubDeliveryManagerId IS NULL AND r.delivery.deletedAt IS NULL")
    List<UUID> findDeliveryIdsWithUnassignedRoutes();
}
