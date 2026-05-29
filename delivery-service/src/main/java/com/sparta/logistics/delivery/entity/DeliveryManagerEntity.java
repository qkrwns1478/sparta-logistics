package com.sparta.logistics.delivery.entity;

import com.sparta.logistics.common.domain.BaseEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_delivery_manager")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryManagerEntity extends BaseEntity {

    @Id
    private UUID id; // 사용자 ID와 동일 (별도 생성 없음)

    @Column(nullable = false)
    private UUID hubId;

    @Column(length = 100)
    private String slackId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryManagerType managerType;

    @Column(nullable = false)
    private int deliverySequence;

    @Column
    private LocalDateTime lastAssignedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryManagerStatus status = DeliveryManagerStatus.IDLE;

    @Version
    private long version; // 낙관적 락 — 동시 배정 충돌 방지

    public DeliveryManagerEntity(UUID userId, UUID hubId, String slackId,
                                 DeliveryManagerType managerType, int deliverySequence) {
        this.id = userId;
        this.hubId = hubId;
        this.slackId = slackId;
        this.managerType = managerType;
        this.deliverySequence = deliverySequence;
        this.status = DeliveryManagerStatus.IDLE;
    }

    public void updateInfo(UUID hubId, String slackId) {
        this.hubId = hubId;
        this.slackId = slackId;
    }

    public void changeStatus(DeliveryManagerStatus next) {
        this.status = next;
    }

    public void assign() {
        this.status = DeliveryManagerStatus.WORKING;
        this.deliverySequence += 1;
        this.lastAssignedAt = LocalDateTime.now();
    }

    public void completeAssignment() {
        this.status = DeliveryManagerStatus.IDLE;
    }

    public void delete(UUID actorId) {
        softDelete(actorId);
        this.status = DeliveryManagerStatus.WITHDRAWN;
    }
}
