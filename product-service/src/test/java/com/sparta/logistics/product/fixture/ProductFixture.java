package com.sparta.logistics.product.fixture;

import com.sparta.logistics.product.entity.Product;

import java.util.UUID;

public class ProductFixture {

    // 기본값이 세팅된 Builder 반환
    public static Product.ProductBuilder builder(
            UUID companyId,
            UUID hubId) {
        return Product.builder()
                .name("테스트 상품")
                .companyId(companyId)
                .hubId(hubId)
                .price(10000L)
                .description("테스트 상품 설명");
    }

    // 가장 많이 쓰는 기본 생성
    public static Product create(
            String name,
            UUID companyId,
            UUID hubId) {
        return builder(companyId, hubId)
                .name(name)
                .build();
    }
}
