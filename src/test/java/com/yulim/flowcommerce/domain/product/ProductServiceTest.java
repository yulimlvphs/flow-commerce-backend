package com.yulim.flowcommerce.domain.product;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.dto.ProductUpdateRequest;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.product.service.ProductService;
import com.yulim.flowcommerce.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {
    @Mock ProductRepository products;
    @Mock DealRepository deals;
    ProductService service;
    Product product;
    final Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @BeforeEach
    void setup() {
        service = new ProductService(products, deals, clock);
        product = Product.builder().sellerId(10L).name("상품").originalPrice(BigDecimal.valueOf(1000)).build();
        ReflectionTestUtils.setField(product, "id", 1L);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
    }

    @ParameterizedTest
    @EnumSource(value = Deal.DealStatus.class, names = {"SCHEDULED", "OPEN", "SOLD_OUT"})
    void activeDealPreventsDeletionAndPriceChange(Deal.DealStatus status) {
        when(deals.existsByProductIdAndStatusIn(eq(1L), anyList()))
                .thenAnswer(call -> ((List<?>) call.getArgument(1)).contains(status));
        assertThatThrownBy(() -> service.deleteProduct(1L, 10L))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(PRODUCT_HAS_ACTIVE_DEAL));
        assertThatThrownBy(() -> service.updateProduct(1L, 10L, patch(BigDecimal.valueOf(1200))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(PRODUCT_PRICE_LOCKED));
        assertThat(product.isActive()).isTrue();
        assertThat(product.getOriginalPrice()).isEqualByComparingTo("1000");
    }

    @Test
    void unchangedPriceWithDifferentScaleAndOtherFieldUpdatesAreAllowed() {
        service.updateProduct(1L, 10L, patch(new BigDecimal("1000.00")));
        service.updateProduct(1L, 10L, patch(null));
        assertThat(product.getName()).isEqualTo("수정 상품");
        verifyNoInteractions(deals);
    }

    @Test
    void closedDealAllowsPriceChangeAndDeletionUsesInjectedClock() {
        when(deals.existsByProductIdAndStatusIn(eq(1L), anyList()))
                .thenAnswer(call -> ((List<?>) call.getArgument(1)).contains(Deal.DealStatus.CLOSED));
        service.updateProduct(1L, 10L, patch(BigDecimal.valueOf(1200)));
        assertThat(product.getOriginalPrice()).isEqualByComparingTo("1200");
        service.deleteProduct(1L, 10L);
        assertThat(product.isActive()).isFalse();
        assertThat(product.getDeletedAt()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    void ownershipIsCheckedBeforeChangingPrice() {
        assertThatThrownBy(() -> service.updateProduct(1L, 99L, patch(BigDecimal.valueOf(1200))))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(PRODUCT_ACCESS_DENIED));
        verifyNoInteractions(deals);
    }

    private ProductUpdateRequest patch(BigDecimal price) {
        return new ProductUpdateRequest("수정 상품", null, price, null);
    }
}
