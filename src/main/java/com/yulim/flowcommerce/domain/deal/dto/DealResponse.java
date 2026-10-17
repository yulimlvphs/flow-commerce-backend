package com.yulim.flowcommerce.domain.deal.dto;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.product.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealResponse(
        Long dealId,
        Long productId,
        String productName,
        String productDescription,
        String thumbnailUrl,
        BigDecimal originalPrice, // 정가
        BigDecimal dealPrice, // 할인가
        int discountRate, // 할인율
        Integer totalStock,
        Integer remainingStock,
        Integer maxPurchasePerUser, // 수량 선택 상한
        LocalDateTime openAt,
        LocalDateTime closeAt,
        Integer holdDurationMinutes,
        Deal.DisplayStatus displayStatus,
        LocalDateTime serverNow
) {
    public static DealResponse from(Deal deal, Product product, LocalDateTime now) {
        return new DealResponse(
                deal.getId(),
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getThumbnailUrl(),
                product.getOriginalPrice(),
                deal.getDealPrice(),
                deal.discountRate(product.getOriginalPrice()),
                deal.getTotalStock(),
                deal.getRemainingStock(),
                deal.getMaxPurchasePerUser(),
                deal.getOpenAt(),
                deal.getCloseAt(),
                deal.getHoldDurationMinutes(),
                deal.displayStatus(now),
                now
        );
    }
}
