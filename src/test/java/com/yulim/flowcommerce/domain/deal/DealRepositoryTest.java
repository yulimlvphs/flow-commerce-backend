package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DealRepositoryTest {
    @Autowired DealRepository deals;
    private static final LocalDateTime START = LocalDateTime.of(2030, 1, 1, 12, 0);
    private static final List<Deal.DealStatus> ACTIVE = List.of(SCHEDULED, OPEN, SOLD_OUT);

    @Test
    void overlapIsHalfOpenAndExcludesSelfOtherProductsAndCancelledDeals() {
        Deal deal = deals.saveAndFlush(Deal.builder().productId(101L).dealPrice(BigDecimal.TEN)
                .totalStock(10).maxPurchasePerUser(1).holdDurationMinutes(5)
                .openAt(START).closeAt(START.plusHours(1)).build());
        assertThat(deals.existsOverlapping(101L, null, ACTIVE, START, START.plusMinutes(30))).isTrue();
        assertThat(deals.existsOverlapping(101L, null, ACTIVE, START.minusMinutes(30), START)).isFalse();
        assertThat(deals.existsOverlapping(101L, null, ACTIVE, START.plusHours(1), START.plusHours(2))).isFalse();
        assertThat(deals.existsOverlapping(101L, deal.getId(), ACTIVE, START, START.plusHours(1))).isFalse();
        assertThat(deals.existsOverlapping(102L, null, ACTIVE, START, START.plusHours(1))).isFalse();
        deal.cancel(START.minusMinutes(1));
        deals.flush();
        assertThat(deals.existsOverlapping(101L, null, ACTIVE, START, START.plusHours(1))).isFalse();
    }
}
