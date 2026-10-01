package com.yulim.flowcommerce.domain.product.dto;

import com.yulim.flowcommerce.domain.product.entity.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 200, message = "상품명은 200자를 넘을 수 없습니다.")
        String name,

        @Size(max = 2000, message = "설명은 2000자를 넘을 수 없습니다.")
        String description,

        @NotNull(message = "가격은 필수입니다.")
        @Positive(message = "가격은 0보다 커야 합니다.")
        BigDecimal originalPrice,

        @Size(max = 500, message = "썸네일 URL은 500자를 넘을 수 없습니다.")
        String thumbnailUrl
) {
    public Product toEntity(Long sellerId) {
        return Product.builder()
                .sellerId(sellerId)
                .name(name)
                .description(description)
                .originalPrice(originalPrice)
                .thumbnailUrl(thumbnailUrl)
                .build();
    }
}