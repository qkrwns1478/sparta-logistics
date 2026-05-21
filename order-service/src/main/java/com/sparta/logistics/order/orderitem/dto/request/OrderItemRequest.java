package com.sparta.logistics.order.orderitem.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

import java.util.UUID;

@Getter
public class OrderItemRequest {

    @NotNull(message = "상품 ID는 필수입니다.")
    private UUID productId;

    // TODO: product-service 연동 후 삭제
    @NotBlank(message = "상품명은 필수입니다.")
    private String productName;

    // TODO: product-service 연동 후 삭제
    @NotNull(message = "단가는 필수입니다.")
    @Positive(message = "단가는 0보다 커야 합니다.")
    private Long unitPrice;

    @NotNull(message = "수량은 필수입니다.")
    @Min(value = 1, message = "수량은 최소 1 이상이어야 합니다.")
    private Integer quantity;
}
