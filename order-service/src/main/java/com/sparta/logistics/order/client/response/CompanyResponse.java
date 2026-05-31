package com.sparta.logistics.order.client.response;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.UUID;

public record CompanyResponse(
        UUID companyId,
        String name,
        String type,
        @JsonAlias({"hub_id", "hubId"})
        UUID hubId,
        String hubName,
        String address,
        String status
) {}
