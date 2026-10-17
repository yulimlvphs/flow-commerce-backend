package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.support.FixedClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
@Transactional
class DealQueryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;
    @Autowired DealRepository deals;
    @Autowired Clock clock;

    @Test
    void anonymousReadsTimeBasedStatusEvenWhenSchedulerIsDelayed() throws Exception {
        Product product = product();
        Deal deal = deal(product, now().minusHours(1), now().plusHours(1));
        mvc.perform(get("/api/deals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].dealId").value(deal.getId()))
                .andExpect(jsonPath("$.data.content[0].productName").value("딜 상품"));
        mvc.perform(get("/api/deals/{id}", deal.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(product.getId()))
                .andExpect(jsonPath("$.data.productDescription").value("딜 설명"))
                .andExpect(jsonPath("$.data.originalPrice").value(1000))
                .andExpect(jsonPath("$.data.dealPrice").value(700))
                .andExpect(jsonPath("$.data.discountRate").value(30))
                .andExpect(jsonPath("$.data.totalStock").value(10))
                .andExpect(jsonPath("$.data.remainingStock").value(10))
                .andExpect(jsonPath("$.data.holdDurationMinutes").value(10))
                .andExpect(jsonPath("$.data.serverNow").value("2030-01-01T09:00:00"))
                .andExpect(jsonPath("$.data.displayStatus").value("ON_SALE"));
    }

    @Test
    void filtersUseTimeStockAndExcludeCancelledOrEndedDeals() throws Exception {
        Product product = product();
        Deal upcoming = deal(product, now().plusHours(1), now().plusHours(3));
        Deal onSale = deal(product, now(), now().plusHours(2));
        Deal soldOut = deal(product, now().minusHours(1), now().plusHours(1));
        ReflectionTestUtils.setField(soldOut, "remainingStock", 0);
        Deal ended = deal(product, now().minusHours(1), now());
        Deal cancelled = deal(product, now().plusHours(1), now().plusHours(2));
        cancelled.cancel(now());
        mvc.perform(get("/api/deals").param("status", "UPCOMING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].dealId").value(upcoming.getId()));
        mvc.perform(get("/api/deals").param("status", "ON_SALE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].dealId").value(onSale.getId()));
        mvc.perform(get("/api/deals").param("status", "SOLD_OUT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].dealId").value(soldOut.getId()));
        mvc.perform(get("/api/deals").param("size", "100").param("sort", "id,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.size").value(50))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].dealId").value(soldOut.getId()))
                .andExpect(jsonPath("$.data.content[2].dealId").value(upcoming.getId()));
        for (Deal deal : new Deal[]{ended, cancelled}) {
            mvc.perform(get("/api/deals/{id}", deal.getId()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayStatus").value("CLOSED"));
        }
        mvc.perform(get("/api/deals").param("status", "CLOSED"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void upcomingSortsByOpeningTime() throws Exception {
        Product product = product();
        deal(product, now().plusHours(2), now().plusHours(3));
        Deal first = deal(product, now().plusHours(1), now().plusHours(4));
        mvc.perform(get("/api/deals").param("status", "UPCOMING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].dealId").value(first.getId()));
    }

    @Test
    void inactiveProductsAreExcludedAndMissingDealsReturn404() throws Exception {
        Product product = product();
        Deal deal = deal(product, now().minusHours(1), now().plusHours(1));
        product.deactivate(java.time.LocalDateTime.of(2030, 1, 1, 9, 0));
        mvc.perform(get("/api/deals"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/deals/{id}", deal.getId()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_001"));
        mvc.perform(get("/api/deals/99999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEAL_001"));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private Product product() {
        return products.save(Product.builder().sellerId(123L).name("딜 상품")
                .description("딜 설명").originalPrice(BigDecimal.valueOf(1000)).build());
    }

    private Deal deal(Product product, LocalDateTime open, LocalDateTime close) {
        return deals.save(Deal.builder().productId(product.getId()).dealPrice(BigDecimal.valueOf(700))
                .totalStock(10).maxPurchasePerUser(2).openAt(open)
                .closeAt(close).holdDurationMinutes(10).build());
    }
}
