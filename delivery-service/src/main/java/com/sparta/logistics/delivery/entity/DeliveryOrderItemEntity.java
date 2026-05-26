package com.sparta.logistics.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_id", nullable = false)
    private DeliveryEntity delivery;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID hubId;

    @Column(nullable = false)
    private int quantity;

    public DeliveryOrderItemEntity(DeliveryEntity delivery, UUID productId, UUID hubId, int quantity) {
        this.delivery = delivery;
        this.productId = productId;
        this.hubId = hubId;
        this.quantity = quantity;
    }
}
