package com.yulim.flowcommerce.domain.product.dto;
import com.yulim.flowcommerce.domain.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 상품 목록/상세 조회 공통 응답 DTO.
 */
public record ProductResponse(
        Long id,
        Long sellerId,
        String name,
        String description,
        BigDecimal originalPrice,
        String thumbnailUrl,
        String status,
        LocalDateTime createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getDescription(),
                product.getOriginalPrice(),
                product.getThumbnailUrl(),
                product.getStatus().name(),
                product.getCreatedAt()
        );
    }
}