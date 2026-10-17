package com.yulim.flowcommerce.domain.deal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealTerms(
        BigDecimal dealPrice,
        int totalStock,
        int maxPurchasePerUser,
        LocalDateTime openAt,
        LocalDateTime closeAt,
        int holdDurationMinutes
) {
}
