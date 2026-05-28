package com.sparta.logistics.hub.hubroute.dto.response;

import com.sparta.logistics.hub.hubroute.entity.HubRoute;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HubRouteSegmentResponse {

    private int sequence;
    private boolean lastMile;   // 허브 간 경로는 항상 false임.
    private UUID sourceHubId;
    private UUID destinationHubId;
    private BigDecimal estimatedDistance;
    private Integer estimatedDuration;

    public static HubRouteSegmentResponse of(int sequence, HubRoute hubRoute) {
        return HubRouteSegmentResponse.builder()
                .sequence(sequence)
                .lastMile(false)
                .sourceHubId(hubRoute.getSourceHub().getId())
                .destinationHubId(hubRoute.getDestinationHub().getId())
                .estimatedDistance(hubRoute.getDistance())
                .estimatedDuration(hubRoute.getDuration())
                .build();
    }
}
