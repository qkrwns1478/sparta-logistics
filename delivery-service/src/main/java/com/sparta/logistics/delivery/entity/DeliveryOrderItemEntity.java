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

    // 원래 주문 항목 ID — hub-service 재고 변경 이력(HubStockLog)과 연결용
    // stock.reserved 이벤트를 통해 수신, nullable (레거시 이벤트 대비)
    @Column
    private UUID orderItemId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID hubId;

    @Column(nullable = false)
    private int quantity;

    public DeliveryOrderItemEntity(DeliveryEntity delivery, UUID orderItemId,
                                   UUID productId, UUID hubId, int quantity) {
        this.delivery = delivery;
        this.orderItemId = orderItemId;
        this.productId = productId;
        this.hubId = hubId;
        this.quantity = quantity;
    }
}
