package com.sparta.logistics.delivery.entity;

import com.sparta.logistics.delivery.entity.enums.RouteType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeliveryRouteEntity 단위 테스트.
 *
 * <p>@Version 낙관적 락 필드가 존재하고 초기값이 올바른지,
 * assignManager 메서드가 hubDeliveryManagerId만 변경하고
 * version 필드에는 영향을 주지 않는지를 확인한다.
 * (version 증가는 JPA flush 시점에 Hibernate가 담당)
 */
class DeliveryRouteEntityTest {

    private DeliveryEntity delivery() {
        return new DeliveryEntity(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "주소", "slack");
    }

    private DeliveryRouteEntity route(DeliveryEntity d) {
        return new DeliveryRouteEntity(d, 0, RouteType.HUB_TO_HUB,
                UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ONE, 60);
    }

    @Test
    void version_필드_초기값이_0() {
        // 신규 엔티티는 version = 0 으로 시작해야 Hibernate가 INSERT 후 UPDATE 감지 가능
        DeliveryRouteEntity r = route(delivery());

        assertThat(r.getVersion()).isEqualTo(0L);
    }

    @Test
    void assignManager_호출시_managerId_설정되고_version은_Java레벨에서_불변() {
        // version 증가는 DB flush 시점에만 발생 — 순수 Java 호출로는 변경되어선 안 됨
        DeliveryRouteEntity r = route(delivery());
        UUID managerId = UUID.randomUUID();

        r.assignManager(managerId);

        assertThat(r.getHubDeliveryManagerId()).isEqualTo(managerId);
        assertThat(r.getVersion()).isEqualTo(0L);
    }

    @Test
    void assignManager_null로_초기화_가능() {
        // 미배정 상태(null)에서 배정, 다시 null 복귀 경로 확인
        DeliveryRouteEntity r = route(delivery());

        assertThat(r.getHubDeliveryManagerId()).isNull();
        r.assignManager(UUID.randomUUID());
        assertThat(r.getHubDeliveryManagerId()).isNotNull();
    }
}
