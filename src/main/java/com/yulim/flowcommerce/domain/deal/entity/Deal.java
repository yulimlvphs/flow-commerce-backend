package com.yulim.flowcommerce.domain.deal.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 타임딜 엔티티.
 * remainingStock은 동시성 제어의 핵심 필드이며,
 * 낙관적 락(@Version) 또는 비관적 락(SELECT FOR UPDATE) 전략에 따라
 * 별도 재고 차감 로직(DealStockService 등)에서 원자적으로 갱신한다.
 */
@Entity
@Table(name = "deals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStatus status;

    @Column(nullable = false)
    private BigDecimal dealPrice;

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    private Integer remainingStock;

    @Column(nullable = false)
    private Integer maxPurchasePerUser;

    @Column(nullable = false)
    private LocalDateTime openAt;

    @Column(nullable = false)
    private LocalDateTime closeAt;

    /** 재고 홀딩 유효 시간(분). 주문 생성 시 이 값으로 holdExpiresAt을 계산한다. */
    @Column(nullable = false)
    private Integer holdDurationMinutes;

    @Version
    private Long version; // 낙관적 락용 버전 필드

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Deal(Long productId, BigDecimal dealPrice, Integer totalStock,
                 Integer maxPurchasePerUser, LocalDateTime openAt,
                 LocalDateTime closeAt, Integer holdDurationMinutes) {
        validateTimeRange(openAt, closeAt);
        validateStock(totalStock);

        this.productId = productId;
        this.dealPrice = dealPrice;
        this.totalStock = totalStock;
        this.remainingStock = totalStock;
        this.maxPurchasePerUser = maxPurchasePerUser;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.holdDurationMinutes = holdDurationMinutes;
        this.status = DealStatus.SCHEDULED;
        this.createdAt = LocalDateTime.now();
    }

    private static void validateTimeRange(LocalDateTime openAt, LocalDateTime closeAt) {
        if (openAt == null || closeAt == null || !closeAt.isAfter(openAt)) {
            throw new IllegalArgumentException("종료 시각은 오픈 시각 이후여야 합니다.");
        }
    }

    private static void validateStock(Integer totalStock) {
        if (totalStock == null || totalStock <= 0) {
            throw new IllegalArgumentException("총 재고는 1 이상이어야 합니다.");
        }
    }

    /** 재고 차감. 원자적 갱신을 보장하려면 Repository 레벨에서 벌크 UPDATE + WHERE remaining_stock > 0 조건을 병행 권장. */
    public void decreaseStock(int quantity) {
        if (this.remainingStock < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.remainingStock -= quantity;
        if (this.remainingStock == 0) {
            this.status = DealStatus.SOLD_OUT;
        }
    }

    public void restoreStock(int quantity) {
        this.remainingStock += quantity;
        if (this.status == DealStatus.SOLD_OUT && this.remainingStock > 0) {
            this.status = DealStatus.OPEN;
        }
    }

    public void open() {
        this.status = DealStatus.OPEN;
    }

    public void close() {
        this.status = DealStatus.CLOSED;
    }

    public boolean isPurchasable() {
        return this.status == DealStatus.OPEN && this.remainingStock > 0;
    }

    public enum DealStatus {
        SCHEDULED, OPEN, SOLD_OUT, CLOSED
    }
}
