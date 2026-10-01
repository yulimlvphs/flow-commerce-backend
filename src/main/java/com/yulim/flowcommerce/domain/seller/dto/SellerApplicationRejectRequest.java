package com.yulim.flowcommerce.domain.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SellerApplicationRejectRequest(

        @NotBlank(message = "거절 사유는 필수입니다.")
        @Size(max = 500, message = "거절 사유는 500자를 넘을 수 없습니다.")
        String reason
) {
}