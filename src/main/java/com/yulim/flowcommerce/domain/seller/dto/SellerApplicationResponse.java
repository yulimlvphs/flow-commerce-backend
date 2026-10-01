package com.yulim.flowcommerce.domain.seller.dto;

import com.yulim.flowcommerce.domain.seller.entity.SellerApplication;

import java.time.LocalDateTime;

public record SellerApplicationResponse(
        Long id,
        Long userId,
        String businessName,
        String businessRegistrationNumber,
        String status,
        String rejectReason,
        LocalDateTime appliedAt,
        LocalDateTime reviewedAt
) {
    public static SellerApplicationResponse from(SellerApplication application) {
        return new SellerApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getBusinessName(),
                application.getBusinessRegistrationNumber(),
                application.getStatus().name(),
                application.getRejectReason(),
                application.getAppliedAt(),
                application.getReviewedAt()
        );
    }
}