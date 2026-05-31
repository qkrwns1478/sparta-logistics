package com.sparta.logistics.delivery.entity;

import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryManagerEntityTest {

    private DeliveryManagerEntity manager() {
        return new DeliveryManagerEntity(UUID.randomUUID(), UUID.randomUUID(), "slack",
                DeliveryManagerType.HUB_DELIVERY, 0);
    }

    @Test
    void 초기_상태는_IDLE() {
        assertThat(manager().getStatus()).isEqualTo(DeliveryManagerStatus.IDLE);
    }

    @Test
    void assign호출시_WORKING으로_전환되고_sequence가_maxPlus1로_설정() {
        DeliveryManagerEntity m = manager();
        m.assign(5);
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.WORKING);
        assertThat(m.getDeliverySequence()).isEqualTo(6);
        assertThat(m.getLastAssignedAt()).isNotNull();
    }

    @Test
    void assign시_sequence는_현재값_무관하게_maxPlus1() {
        // A(seq=0), B(seq=0), C(seq=0) 상황에서 A 배정 후 max=2 → A.seq=3
        DeliveryManagerEntity m = manager(); // seq=0
        m.assign(2);
        assertThat(m.getDeliverySequence()).isEqualTo(3);
    }

    @Test
    void 순환_라운드로빈_배정_후_맨뒤로_이동() {
        // A(0), B(1), C(2) 상황에서 A 배정 시 max=2 → A.seq=3
        // 다음 선택 대상은 B(1)
        DeliveryManagerEntity a = new DeliveryManagerEntity(UUID.randomUUID(), UUID.randomUUID(), "slack",
                DeliveryManagerType.HUB_DELIVERY, 0);
        a.assign(2); // max=2 (B=1, C=2)
        assertThat(a.getDeliverySequence()).isEqualTo(3); // 맨 뒤로 이동
    }

    @Test
    void completeAssignment호출시_IDLE로_복귀() {
        DeliveryManagerEntity m = manager();
        m.assign(0);
        m.completeAssignment();
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.IDLE);
    }

    @Test
    void delete호출시_WITHDRAWN으로_전환() {
        DeliveryManagerEntity m = manager();
        m.delete(UUID.randomUUID());
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.WITHDRAWN);
        assertThat(m.isDeleted()).isTrue();
    }

    @Test
    void WORKING_중_삭제시_WITHDRAWN_유지_IDLE_아님() {
        DeliveryManagerEntity m = manager();
        m.assign(0);
        m.delete(UUID.randomUUID());
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.WITHDRAWN);
    }
}
