package com.sparta.logistics.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_delivery_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryOrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID deliveryId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    public DeliveryOrderItemEntity(UUID deliveryId, UUID productId, int quantity) {
        this.deliveryId = deliveryId;
        this.productId = productId;
        this.quantity = quantity;
    }
}
