package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.dto.DealCreateRequest;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.deal.service.DealService;
import com.yulim.flowcommerce.domain.deal.service.DealTransitionService;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import com.yulim.flowcommerce.support.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
class DealConcurrencyIntegrationTest {
    @Autowired DealRepository deals;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired DealService service;
    @Autowired DealTransitionService transitions;
    @Autowired Clock clock;

    @Test
    void concurrentCreationCommitsOnlyOneOverlappingDeal() throws Exception {
        User user = User.register(UUID.randomUUID() + "@example.com", "unused");
        user.promoteToSeller();
        users.saveAndFlush(user);
        Product product = products.saveAndFlush(Product.builder().sellerId(user.getId()).name("동시 생성 상품")
                .originalPrice(BigDecimal.valueOf(1000)).build());
        LocalDateTime now = LocalDateTime.now(clock);
        var request = new DealCreateRequest(product.getId(), BigDecimal.valueOf(700), 10, 2,
                now.plusHours(1), now.plusHours(2), 10);
        try {
            List<String> outcomes = concurrently(() -> {
                try {
                    service.createDeal(user.getId(), request);
                    return "CREATED";
                } catch (BusinessException exception) {
                    return exception.getErrorCode().name();
                }
            });
            assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "DEAL_PERIOD_OVERLAP");
            assertThat(deals.findAll().stream().filter(d -> d.getProductId().equals(product.getId())).toList()).hasSize(1);
        } finally {
            deals.deleteAllById(deals.findAll().stream().filter(d -> d.getProductId().equals(product.getId())).map(Deal::getId).toList());
            products.deleteById(product.getId());
            users.deleteById(user.getId());
        }
    }

    @Test
    void concurrentSchedulerTransactionsTransitionEachRowOnlyOnce() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Deal opening = deals.saveAndFlush(deal(now, now.plusHours(1)));
        Deal ending = deals.saveAndFlush(deal(now.minusHours(1), now));
        try {
            List<Integer> affected = concurrently(transitions::transitionStatuses);
            assertThat(affected).containsExactlyInAnyOrder(2, 0);
            Deal opened = deals.findById(opening.getId()).orElseThrow();
            Deal closed = deals.findById(ending.getId()).orElseThrow();
            assertThat(opened.getStatus()).isEqualTo(Deal.DealStatus.OPEN);
            assertThat(closed.getStatus()).isEqualTo(Deal.DealStatus.CLOSED);
            assertThat(opened.getVersion()).isEqualTo(opening.getVersion() + 1);
            assertThat(closed.getVersion()).isEqualTo(ending.getVersion() + 1);
        } finally {
            deals.deleteAllById(List.of(opening.getId(), ending.getId()));
        }
    }

    private Deal deal(LocalDateTime open, LocalDateTime close) {
        return Deal.builder().productId(1L).dealPrice(BigDecimal.TEN).totalStock(10)
                .maxPurchasePerUser(1).holdDurationMinutes(5).openAt(open).closeAt(close).build();
    }

    private <T> List<T> concurrently(Callable<T> action) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<T> task = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 요청 시작 시간 초과");
            return action.call();
        };
        try {
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
