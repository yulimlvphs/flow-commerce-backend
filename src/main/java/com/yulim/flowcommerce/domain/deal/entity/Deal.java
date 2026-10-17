package com.yulim.flowcommerce.domain.deal.entity;

import com.yulim.flowcommerce.common.entity.BaseEntity;
import com.yulim.flowcommerce.domain.deal.dto.DealTerms;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 저장 상태는 스케줄러가 동기화하며, 사용자에게 보이는 상태는 요청 시각으로 계산한다.
 */
@Entity
@Table(name = "deals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStatus status;

    @Column(nullable = false)
    private BigDecimal dealPrice; // 할인 판매 가격

    @Column(nullable = false)
    private Integer totalStock;

    @Column(nullable = false)
    private Integer remainingStock; // 남은 재고

    @Column(nullable = false)
    private Integer maxPurchasePerUser; // 수량 선택 상한

    @Column(nullable = false)
    private LocalDateTime openAt; // 판매 시작 시각

    @Column(nullable = false)
    private LocalDateTime closeAt; // 판매 종료 시각

    /** 재고 홀딩 유효 시간(분). 주문 생성 시 이 값으로 holdExpiresAt을 계산한다. */
    @Column(nullable = false)
    private Integer holdDurationMinutes;

    @Version
    private Long version; // 낙관적 락용 버전 필드

    @Builder
    private Deal(Long productId, BigDecimal dealPrice, Integer totalStock,
                 Integer maxPurchasePerUser, LocalDateTime openAt,
                 LocalDateTime closeAt, Integer holdDurationMinutes) {
        validateConfiguration(dealPrice, totalStock, maxPurchasePerUser, openAt, closeAt, holdDurationMinutes);

        this.productId = productId;
        this.dealPrice = dealPrice;
        this.totalStock = totalStock;
        this.remainingStock = totalStock;
        this.maxPurchasePerUser = maxPurchasePerUser;
        this.openAt = openAt;
        this.closeAt = closeAt;
        this.holdDurationMinutes = holdDurationMinutes;
        this.status = DealStatus.SCHEDULED;
    }

    public static Deal create(Long productId, DealTerms terms) {
        return Deal.builder()
                .productId(productId)
                .dealPrice(terms.dealPrice())
                .totalStock(terms.totalStock())
                .maxPurchasePerUser(terms.maxPurchasePerUser())
                .openAt(terms.openAt())
                .closeAt(terms.closeAt())
                .holdDurationMinutes(terms.holdDurationMinutes())
                .build();
    }

    public DealTerms terms() {
        return new DealTerms(dealPrice, totalStock, maxPurchasePerUser, openAt, closeAt, holdDurationMinutes);
    }

    public boolean isPurchasable(LocalDateTime now) {
        return status != DealStatus.CLOSED && !now.isBefore(openAt)
                && now.isBefore(closeAt) && remainingStock > 0;
    }

    public boolean isModifiable(LocalDateTime now) {
        return status == DealStatus.SCHEDULED && now.isBefore(openAt);
    }

    public DisplayStatus displayStatus(LocalDateTime now) {
        if (status == DealStatus.CLOSED || !now.isBefore(closeAt)) {
            return DisplayStatus.CLOSED;
        }
        if (now.isBefore(openAt)) {
            return DisplayStatus.UPCOMING;
        }
        return remainingStock > 0 ? DisplayStatus.ON_SALE : DisplayStatus.SOLD_OUT;
    }

    public void update(DealTerms terms, LocalDateTime now) {
        validateModifiable(now);
        validateConfiguration(terms.dealPrice(), terms.totalStock(), terms.maxPurchasePerUser(),
                terms.openAt(), terms.closeAt(), terms.holdDurationMinutes());
        if (!terms.openAt().isAfter(now)) {
            throw new IllegalArgumentException("오픈 시각은 현재 이후여야 합니다.");
        }
        this.dealPrice = terms.dealPrice();
        this.totalStock = terms.totalStock();
        this.remainingStock = terms.totalStock();
        this.maxPurchasePerUser = terms.maxPurchasePerUser();
        this.openAt = terms.openAt();
        this.closeAt = terms.closeAt();
        this.holdDurationMinutes = terms.holdDurationMinutes();
    }

    public void cancel(LocalDateTime now) {
        validateModifiable(now);
        this.status = DealStatus.CLOSED;
    }

    private void validateModifiable(LocalDateTime now) {
        if (!isModifiable(now)) {
            throw new IllegalStateException("시작 전 예정된 딜만 수정하거나 취소할 수 있습니다.");
        }
    }

    /** 유효 기간 내 품절 딜의 재고가 복원되면 판매를 재개한다. */
    public void restoreStock(int quantity, LocalDateTime now) {
        if (quantity <= 0 || quantity > totalStock - remainingStock) {
            throw new IllegalArgumentException("복원 수량이 올바르지 않습니다.");
        }
        this.remainingStock += quantity;
        if (status == DealStatus.SOLD_OUT && !now.isBefore(openAt) && now.isBefore(closeAt)) {
            this.status = DealStatus.OPEN;
        }
    }

    public int discountRate(BigDecimal originalPrice) {
        if (originalPrice == null || originalPrice.signum() <= 0) {
            throw new IllegalArgumentException("정가는 0보다 커야 합니다.");
        }
        // 할인율은 정수 백분율로 계산하며 소수점 이하는 버린다.
        return originalPrice.subtract(dealPrice).multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.DOWN).intValueExact();
    }

    private static void validateConfiguration(BigDecimal price, Integer stock, Integer limit,
                                              LocalDateTime openAt, LocalDateTime closeAt, Integer holdMinutes) {
        validateTimeRange(openAt, closeAt);
        validateStock(stock);
        if (price == null || price.signum() <= 0 || limit == null || limit < 1 || limit > stock
                || holdMinutes == null || holdMinutes < 1 || holdMinutes > 30) {
            throw new IllegalArgumentException("딜 설정이 올바르지 않습니다.");
        }
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

    public enum DealStatus {
        SCHEDULED, OPEN, SOLD_OUT, CLOSED
    }

    public enum DisplayStatus {
        UPCOMING, ON_SALE, SOLD_OUT, CLOSED
    }
}
