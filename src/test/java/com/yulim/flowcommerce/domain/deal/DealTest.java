package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.dto.DealTerms;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static com.yulim.flowcommerce.domain.deal.entity.Deal.DisplayStatus.*;

class DealTest {
    private static final LocalDateTime OPEN = LocalDateTime.of(2030, 1, 1, 12, 0);

    @Test
    void displayStatusUsesTimeAndStockIncludingExactBoundaries() {
        Deal deal = deal();
        assertThat(deal.displayStatus(OPEN.minusNanos(1))).isEqualTo(UPCOMING);
        assertThat(deal.displayStatus(OPEN)).isEqualTo(ON_SALE);
        ReflectionTestUtils.setField(deal, "remainingStock", 0);
        assertThat(deal.displayStatus(OPEN)).isEqualTo(SOLD_OUT);
        assertThat(deal.displayStatus(OPEN.plusHours(1))).isEqualTo(CLOSED);
    }

    @Test
    void delayedSchedulerDoesNotAllowModificationOrPreventPurchase() {
        Deal deal = deal();
        assertThat(deal.getStatus()).isEqualTo(Deal.DealStatus.SCHEDULED);
        assertThat(deal.isPurchasable(OPEN)).isTrue();
        assertThat(deal.isModifiable(OPEN)).isFalse();
        assertThat(deal.isPurchasable(OPEN.plusHours(1))).isFalse();
        assertThatThrownBy(() -> deal.cancel(OPEN)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellationWinsOverTimeAndStock() {
        Deal deal = deal();
        deal.cancel(OPEN.minusMinutes(1));
        assertThat(deal.displayStatus(OPEN.minusMinutes(1))).isEqualTo(CLOSED);
        assertThat(deal.isPurchasable(OPEN)).isFalse();
        assertThat(deal.isModifiable(OPEN.minusMinutes(1))).isFalse();
    }

    @Test
    void restoringStockAtClosingTimeDoesNotReopenSoldOutDeal() {
        Deal deal = deal();
        ReflectionTestUtils.setField(deal, "status", Deal.DealStatus.SOLD_OUT);
        ReflectionTestUtils.setField(deal, "remainingStock", 0);
        deal.restoreStock(1, OPEN.plusHours(1));
        assertThat(deal.getRemainingStock()).isEqualTo(1);
        assertThat(deal.getStatus()).isEqualTo(Deal.DealStatus.SOLD_OUT);
        assertThat(deal.displayStatus(OPEN.plusHours(1))).isEqualTo(CLOSED);
    }

    @Test
    void changingStockBeforeOpeningResetsRemainingStock() {
        Deal deal = deal();
        deal.update(new DealTerms(BigDecimal.valueOf(700), 20, 3, OPEN, OPEN.plusHours(1), 5), OPEN.minusMinutes(1));
        assertThat(deal.getTotalStock()).isEqualTo(20);
        assertThat(deal.getRemainingStock()).isEqualTo(20);
    }

    @Test
    void discountRateIsCalculatedFromCurrentOriginalPrice() {
        assertThat(deal().discountRate(BigDecimal.valueOf(1000))).isEqualTo(30);
        assertThat(deal().discountRate(BigDecimal.valueOf(999))).isEqualTo(29);
        assertThatThrownBy(() -> deal().discountRate(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }

    private Deal deal() {
        return Deal.builder().productId(1L).dealPrice(BigDecimal.valueOf(700))
                .totalStock(10).maxPurchasePerUser(2).holdDurationMinutes(10)
                .openAt(OPEN).closeAt(OPEN.plusHours(1)).build();
    }
}
