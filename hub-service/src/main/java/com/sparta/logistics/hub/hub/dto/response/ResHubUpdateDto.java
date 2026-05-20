package com.sparta.logistics.hub.hub.dto.response;

import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hub.enums.HubStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ResHubUpdateDto {

    private UUID hubId;
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private HubStatus status;
    private LocalDateTime updatedAt;

    public static ResHubUpdateDto from(Hub hub) {
        return ResHubUpdateDto.builder()
                .hubId(hub.getId())
                .name(hub.getName())
                .address(hub.getAddress())
                .latitude(hub.getLatitude())
                .longitude(hub.getLongitude())
                .status(hub.getStatus())
                .updatedAt(hub.getUpdatedAt())
                .build();
    }
}
