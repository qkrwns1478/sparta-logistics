package com.sparta.logistics.hub.hubroute.dto;

import com.sparta.logistics.hub.hubroute.dto.response.HubRouteSegmentResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HubRouteSegmentListCacheDto {
  private List<HubRouteSegmentResponse> segments;
}
