package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.domain.deal.dto.DealSearchStatus;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.deal.service.DealQueryService;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealQueryServiceTest {
    @Mock DealRepository deals;
    @Mock ProductRepository products;
    DealQueryService service;
    Product product;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @BeforeEach
    void setup() {
        service = new DealQueryService(deals, products, CLOCK);
        product = Product.builder().sellerId(10L).name("상품").originalPrice(BigDecimal.valueOf(1000)).build();
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    @Test
    void listLoadsDistinctProductIdsOnceAndUsesSameServerTimeForAllRows() {
        Deal first = Deal.builder().productId(1L).dealPrice(BigDecimal.valueOf(700))
                .totalStock(10).maxPurchasePerUser(2).openAt(NOW.plusHours(1))
                .closeAt(NOW.plusHours(2)).holdDurationMinutes(10).build();
        Deal second = Deal.builder().productId(1L).dealPrice(BigDecimal.valueOf(600))
                .totalStock(20).maxPurchasePerUser(2).openAt(NOW.plusHours(3))
                .closeAt(NOW.plusHours(4)).holdDurationMinutes(10).build();
        when(deals.findVisible(eq(NOW), eq("UPCOMING"), eq(Product.ProductStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(first, second)));
        when(products.findAllById(List.of(1L))).thenReturn(List.of(product));
        var result = service.getDeals(DealSearchStatus.UPCOMING, PageRequest.of(0, 100));
        assertThat(result.getContent()).allSatisfy(row -> {
            assertThat(row.serverNow()).isEqualTo(NOW);
            assertThat(row.productName()).isEqualTo("상품");
        });
        verify(products).findAllById(List.of(1L));
        verify(products, never()).findById(any());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deals).findVisible(eq(NOW), eq("UPCOMING"), eq(Product.ProductStatus.ACTIVE), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("openAt").isAscending()).isTrue();
    }
}
