package com.sparta.logistics.company.fixture;

import com.sparta.logistics.company.entity.Company;
import com.sparta.logistics.company.entity.CompanyType;

import java.math.BigDecimal;
import java.util.UUID;

public class CompanyFixture {

    private static final String DEFAULT_ADDRESS = "서울시 송파구 송파대로 12";
    private static final BigDecimal DEFAULT_LAT = BigDecimal.valueOf(35.104437);
    private static final BigDecimal DEFAULT_LNG = BigDecimal.valueOf(128.960721);
    private static final CompanyType DEFAULT_TYPE = CompanyType.PRODUCER;

    // 1) builder 제공 (핵심)
    public static Company.CompanyBuilder builder(UUID hubId) {
        return Company.builder()
                .type(DEFAULT_TYPE)
                .hubId(hubId)
                .address(DEFAULT_ADDRESS)
                .latitude(DEFAULT_LAT)
                .longitude(DEFAULT_LNG);
    }

    // 2) 가장 많이 쓰는 기본 생성
    public static Company create(String name, UUID hubId) {
        return builder(hubId)
                .name(name)
                .build();
    }
}
