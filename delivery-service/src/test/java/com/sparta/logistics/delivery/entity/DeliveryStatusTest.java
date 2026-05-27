package com.sparta.logistics.delivery.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStatusTest {

    @Test
    void HUB_MOVING_이상은_취소_불가() {
        assertThat(DeliveryStatus.HUB_MOVING.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.DESTINATION_HUB_ARRIVED.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.OUT_FOR_DELIVERY.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
        assertThat(DeliveryStatus.COMPLETED.canTransitionTo(DeliveryStatus.CANCELLED)).isFalse();
    }

    @Test
    void CREATED_HUB_WAITING은_취소_가능() {
        assertThat(DeliveryStatus.CREATED.canTransitionTo(DeliveryStatus.CANCELLED)).isTrue();
        assertThat(DeliveryStatus.HUB_WAITING.canTransitionTo(DeliveryStatus.CANCELLED)).isTrue();
    }
}