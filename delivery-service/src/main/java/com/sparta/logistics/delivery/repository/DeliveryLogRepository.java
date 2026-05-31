package com.sparta.logistics.delivery.repository;

import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLogEntity, UUID> {

    List<DeliveryLogEntity> findAllByDeliveryIdOrderByRecordedAtAsc(UUID deliveryId);
}
