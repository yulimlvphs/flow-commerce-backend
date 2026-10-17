package com.yulim.flowcommerce.domain.deal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DealCreateRequest(

        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "딜 가격은 필수입니다.")
        BigDecimal dealPrice,

        @NotNull(message = "총 재고는 필수입니다.")
        @Min(value = 1, message = "총 재고는 1 이상이어야 합니다.")
        Integer totalStock,

        @NotNull(message = "1인 구매 제한은 필수입니다.")
        @Min(value = 1, message = "1인 구매 제한은 1 이상이어야 합니다.")
        Integer maxPurchasePerUser,

        @NotNull(message = "오픈 시각은 필수입니다.")
        LocalDateTime openAt,

        @NotNull(message = "종료 시각은 필수입니다.")
        LocalDateTime closeAt,

        @NotNull(message = "재고 홀딩 시간은 필수입니다.")
        @Min(value = 1, message = "재고 홀딩 시간은 1분 이상이어야 합니다.")
        @Max(value = 30, message = "재고 홀딩 시간은 30분을 넘을 수 없습니다.")
        Integer holdDurationMinutes
) {
    public DealTerms toTerms() {
        return new DealTerms(dealPrice, totalStock, maxPurchasePerUser, openAt, closeAt, holdDurationMinutes);
    }
}
