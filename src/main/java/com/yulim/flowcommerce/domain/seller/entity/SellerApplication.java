package com.yulim.flowcommerce.domain.seller.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/* 판매자 전환 신청 엔티티 */
@Entity
@Table(name = "seller_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String businessName;

    /** 사업자등록번호. 형식 "000-00-00000" 10자리 숫자 기준. */
    @Column(nullable = false, length = 20)
    private String businessRegistrationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(length = 500)
    private String rejectReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    private LocalDateTime reviewedAt;

    /** 심사한 관리자 ID. 심사 전에는 null. */
    private Long reviewerId;

    @Builder
    private SellerApplication(Long userId, String businessName, String businessRegistrationNumber) {
        this.userId = userId;
        this.businessName = businessName;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.status = ApplicationStatus.PENDING;
        this.appliedAt = LocalDateTime.now();
    }

    public void approve(Long reviewerId) {
        validatePending();
        this.status = ApplicationStatus.APPROVED;
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(Long reviewerId, String reason) {
        validatePending();
        this.status = ApplicationStatus.REJECTED;
        this.reviewerId = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.rejectReason = reason;
    }

    private void validatePending() {
        if (this.status != ApplicationStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신청입니다. status=" + this.status);
        }
    }

    public enum ApplicationStatus {
        PENDING, APPROVED, REJECTED
    }
}
