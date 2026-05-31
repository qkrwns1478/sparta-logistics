package com.sparta.logistics.order.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 주문-배송 매핑 테이블 (주문 1건 : 배송 N건 구조 대응)
 * <p>
 * delivery.created 이벤트 수신 시마다 레코드를 추가하고,
 * 수신 건수가 totalDeliveryCount에 도달하면 주문을 ACCEPTED로 전이함
 * <p>
 * (orderId, deliveryId) 복합 유니크 제약으로 중복 이벤트 수신 시 재저장 방지
 */
@Entity
@Table(
        name = "p_order_delivery",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_order_delivery",
                columnNames = {"order_id", "delivery_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDelivery {

    @Id
    private UUID id;

    @Column(nullable = false, name = "order_id")
    private UUID orderId;

    @Column(nullable = false, name = "delivery_id")
    private UUID deliveryId;

    public static OrderDelivery of(UUID orderId, UUID deliveryId) {
        OrderDelivery od = new OrderDelivery();
        od.id = UUID.randomUUID();
        od.orderId = orderId;
        od.deliveryId = deliveryId;
        return od;
    }
}
