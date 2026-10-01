package com.yulim.flowcommerce.domain.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SellerApplicationRequest(

        @NotBlank(message = "사업자명은 필수입니다.")
        @Size(max = 100, message = "사업자명은 100자를 넘을 수 없습니다.")
        String businessName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = "\\d{3}-\\d{2}-\\d{5}", message = "사업자등록번호 형식이 올바르지 않습니다. 예: 123-45-67890")
        String businessRegistrationNumber
) {
}