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
    void assign호출시_WORKING으로_전환되고_sequence_증가() {
        DeliveryManagerEntity m = manager();
        int before = m.getDeliverySequence();
        m.assign();
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.WORKING);
        assertThat(m.getDeliverySequence()).isEqualTo(before + 1);
        assertThat(m.getLastAssignedAt()).isNotNull();
    }

    @Test
    void completeAssignment호출시_IDLE로_복귀() {
        DeliveryManagerEntity m = manager();
        m.assign();
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
        m.assign();
        m.delete(UUID.randomUUID());
        assertThat(m.getStatus()).isEqualTo(DeliveryManagerStatus.WITHDRAWN);
    }
}
