package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.deal.service.DealTransitionService;
import com.yulim.flowcommerce.support.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import static com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
@Transactional
class DealSchedulerIntegrationTest {
    @Autowired DealRepository deals;
    @Autowired DealTransitionService service;
    @Autowired Clock clock;

    @Test
    void conditionalUpdatesAreIdempotentAndIncrementVersionExactlyOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        Deal opening = save(SCHEDULED, now, now.plusHours(1));
        Deal skippedOpening = save(SCHEDULED, now.minusHours(2), now);
        Deal closingOpen = save(OPEN, now.minusHours(2), now.minusHours(1));
        Deal closingSoldOut = save(SOLD_OUT, now.minusHours(2), now);
        Deal future = save(SCHEDULED, now.plusHours(1), now.plusHours(2));
        Deal alreadyClosed = save(CLOSED, now.minusHours(2), now.minusHours(1));
        List<Deal> changed = List.of(opening, skippedOpening, closingOpen, closingSoldOut);
        List<Long> versions = changed.stream().map(Deal::getVersion).toList();

        assertThat(service.transitionStatuses()).isEqualTo(4);
        assertThat(reload(opening).getStatus()).isEqualTo(OPEN);
        for (Deal expired : List.of(skippedOpening, closingOpen, closingSoldOut)) {
            assertThat(reload(expired).getStatus()).isEqualTo(CLOSED);
        }
        for (int i = 0; i < changed.size(); i++) {
            Deal reloaded = reload(changed.get(i));
            assertThat(reloaded.getVersion()).isEqualTo(versions.get(i) + 1);
            assertThat(reloaded.getUpdatedAt()).isEqualTo(clock.instant());
        }
        assertThat(reload(future).getStatus()).isEqualTo(SCHEDULED);
        assertThat(reload(future).getVersion()).isEqualTo(future.getVersion());
        assertThat(reload(alreadyClosed).getVersion()).isEqualTo(alreadyClosed.getVersion());
        assertThat(service.transitionStatuses()).isZero();
        for (int i = 0; i < changed.size(); i++) {
            assertThat(reload(changed.get(i)).getVersion()).isEqualTo(versions.get(i) + 1);
        }
    }

    @Test
    void bulkTransitionInvalidatesAnEntityLoadedWithThePreviousVersion() {
        LocalDateTime now = LocalDateTime.now(clock);
        Deal stale = save(SCHEDULED, now, now.plusHours(1));
        assertThat(service.transitionStatuses()).isEqualTo(1);
        // 벌크 UPDATE 전에 읽은 엔티티는 clearAutomatically에 의해 분리된 상태다.
        stale.cancel(now.minusSeconds(1));
        assertThatThrownBy(() -> deals.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    private Deal reload(Deal deal) { return deals.findById(deal.getId()).orElseThrow(); }

    private Deal save(Deal.DealStatus status, LocalDateTime open, LocalDateTime close) {
        Deal deal = Deal.builder().productId(1L).dealPrice(BigDecimal.TEN).totalStock(10)
                .maxPurchasePerUser(1).holdDurationMinutes(5).openAt(open).closeAt(close).build();
        ReflectionTestUtils.setField(deal, "status", status);
        if (status == SOLD_OUT) ReflectionTestUtils.setField(deal, "remainingStock", 0);
        return deals.saveAndFlush(deal);
    }
}
