package com.sparta.logistics.delivery.dto.manager;

import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class DeliveryManagerSearchCond {
    private DeliveryManagerType managerType;
    private DeliveryManagerStatus status;

    // Role에 의해 서비스에서 강제 주입 — 쿼리 파라미터로 받지 않음
    private UUID authorizedHubId;
}
