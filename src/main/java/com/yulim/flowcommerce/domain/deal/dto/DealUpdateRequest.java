package com.yulim.flowcommerce.domain.deal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealUpdateRequest(

        BigDecimal dealPrice,

        @Min(value = 1, message = "총 재고는 1 이상이어야 합니다.")
        Integer totalStock,

        @Min(value = 1, message = "1인 구매 제한은 1 이상이어야 합니다.")
        Integer maxPurchasePerUser,

        LocalDateTime openAt,

        LocalDateTime closeAt,

        @Min(value = 1, message = "재고 홀딩 시간은 1분 이상이어야 합니다.")
        @Max(value = 30, message = "재고 홀딩 시간은 30분을 넘을 수 없습니다.")
        Integer holdDurationMinutes
) {
    /** PATCH 의미: null 인 필드는 현재 값을 유지한다. */
    public DealTerms mergeInto(DealTerms current) {
        return new DealTerms(
                orCurrent(dealPrice, current.dealPrice()),
                orCurrent(totalStock, current.totalStock()),
                orCurrent(maxPurchasePerUser, current.maxPurchasePerUser()),
                orCurrent(openAt, current.openAt()),
                orCurrent(closeAt, current.closeAt()),
                orCurrent(holdDurationMinutes, current.holdDurationMinutes()));
    }

    private static <T> T orCurrent(T requested, T current) {
        return requested == null ? current : requested;
    }
}
