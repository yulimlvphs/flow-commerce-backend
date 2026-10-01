package com.yulim.flowcommerce.domain.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequest(

        @Size(max = 100, message = "상품명은 200자를 넘을 수 없습니다.")
        @NotBlank
        String name,

        @Size(max = 2000, message = "설명은 2000자를 넘을 수 없습니다.")
        String description,

        @Positive(message = "가격은 0보다 커야 합니다.")
        BigDecimal originalPrice,

        @Size(max = 500, message = "썸네일 URL은 500자를 넘을 수 없습니다.")
        String thumbnailUrl
) {
}
