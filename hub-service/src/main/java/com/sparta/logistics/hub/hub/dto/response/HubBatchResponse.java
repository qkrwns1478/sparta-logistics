package com.sparta.logistics.hub.hub.dto.response;

import com.sparta.logistics.hub.hub.entity.Hub;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class HubBatchResponse {
    private UUID hubId;
    private String name;

    public static HubBatchResponse from(Hub hub) {
        return HubBatchResponse.builder()
                .hubId(hub.getId())
                .name(hub.getName())
                .build();
    }
}
