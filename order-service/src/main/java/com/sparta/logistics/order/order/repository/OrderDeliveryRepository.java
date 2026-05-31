package com.sparta.logistics.order.order.repository;

import com.sparta.logistics.order.order.entity.OrderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderDeliveryRepository extends JpaRepository<OrderDelivery, UUID> {

    long countByOrderId(UUID orderId);

    boolean existsByOrderIdAndDeliveryId(UUID orderId, UUID deliveryId);

    void deleteByOrderId(UUID orderId);
}
