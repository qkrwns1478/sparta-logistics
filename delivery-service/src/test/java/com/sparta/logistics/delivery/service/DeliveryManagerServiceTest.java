package com.sparta.logistics.delivery.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.delivery.client.HubServiceClient;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerUpdateRequest;
import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import com.sparta.logistics.delivery.repository.DeliveryManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryManagerServiceTest {

    @Mock DeliveryManagerRepository managerRepository;
    @Mock DeliveryPermissionChecker permissionChecker;
    @Mock HubServiceClient hubServiceClient;

    @InjectMocks DeliveryManagerService service;

    private final UUID managerId = UUID.randomUUID();
    private final UUID originalHubId = UUID.randomUUID();
    private final UUID newHubId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private DeliveryManagerEntity manager;

    @BeforeEach
    void setUp() {
        manager = new DeliveryManagerEntity(managerId, originalHubId, "old-slack",
                DeliveryManagerType.HUB_DELIVERY, 0);
        when(managerRepository.findById(managerId)).thenReturn(Optional.of(manager));
        doNothing().when(permissionChecker).checkManagerSelfWritePermission(any(), any(), any(), any());
    }

    // ── hubId 변경 권한 ──────────────────────────────────────────────────────

    @Test
    void MASTER는_허브_변경_가능() {
        when(permissionChecker.canChangeHubId(Role.MASTER)).thenReturn(true);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(newHubId, "new-slack");

        service.updateManager(managerId, req, actorId, Role.MASTER, null);

        assertThat(manager.getHubId()).isEqualTo(newHubId);
        assertThat(manager.getSlackId()).isEqualTo("new-slack");
    }

    @Test
    void HUB_MANAGER는_허브_변경_가능() {
        when(permissionChecker.canChangeHubId(Role.HUB_MANAGER)).thenReturn(true);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(newHubId, "new-slack");

        service.updateManager(managerId, req, actorId, Role.HUB_MANAGER, originalHubId);

        assertThat(manager.getHubId()).isEqualTo(newHubId);
    }

    @Test
    void DELIVERY_MANAGER는_허브_변경_불가_slackId만_변경() {
        when(permissionChecker.canChangeHubId(Role.DELIVERY_MANAGER)).thenReturn(false);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(newHubId, "new-slack");

        service.updateManager(managerId, req, actorId, Role.DELIVERY_MANAGER, null);

        // hubId는 그대로, slackId만 변경
        assertThat(manager.getHubId()).isEqualTo(originalHubId);
        assertThat(manager.getSlackId()).isEqualTo("new-slack");
    }

    @Test
    void DELIVERY_MANAGER_req에_hubId_null이어도_기존_허브_유지() {
        when(permissionChecker.canChangeHubId(Role.DELIVERY_MANAGER)).thenReturn(false);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(null, "new-slack");

        service.updateManager(managerId, req, actorId, Role.DELIVERY_MANAGER, null);

        assertThat(manager.getHubId()).isEqualTo(originalHubId);
    }

    // ── req.hubId == null 케이스 ─────────────────────────────────────────────

    @Test
    void MASTER도_req에_hubId_null이면_기존_허브_유지() {
        when(permissionChecker.canChangeHubId(Role.MASTER)).thenReturn(true);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(null, "new-slack");

        service.updateManager(managerId, req, actorId, Role.MASTER, null);

        assertThat(manager.getHubId()).isEqualTo(originalHubId);
    }

    // ── slackId null 케이스 ──────────────────────────────────────────────────

    @Test
    void req에_slackId_null이면_기존_slackId_유지() {
        when(permissionChecker.canChangeHubId(Role.MASTER)).thenReturn(true);
        DeliveryManagerUpdateRequest req = new DeliveryManagerUpdateRequest(newHubId, null);

        service.updateManager(managerId, req, actorId, Role.MASTER, null);

        assertThat(manager.getSlackId()).isEqualTo("old-slack");
    }
}
