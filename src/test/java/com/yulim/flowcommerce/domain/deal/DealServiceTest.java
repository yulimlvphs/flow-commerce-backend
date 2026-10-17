package com.yulim.flowcommerce.domain.deal;

import com.yulim.flowcommerce.common.constant.ErrorCode;
import com.yulim.flowcommerce.domain.deal.dto.DealCreateRequest;
import com.yulim.flowcommerce.domain.deal.dto.DealUpdateRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.params.provider.EnumSource;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.deal.service.DealAccessPolicy;
import com.yulim.flowcommerce.domain.deal.service.DealService;
import com.yulim.flowcommerce.domain.deal.service.DealTermsValidator;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {
    @Mock DealRepository deals;
    @Mock ProductRepository products;
    @Mock UserRepository users;
    DealService service;
    Product product;
    static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @BeforeEach
    void setup() {
        service = new DealService(deals, products, new DealAccessPolicy(users), new DealTermsValidator(deals), CLOCK);
        product = Product.builder().sellerId(10L).name("상품").originalPrice(BigDecimal.valueOf(1000)).build();
        ReflectionTestUtils.setField(product, "id", 1L);
    }

    @Test
    void sellerCreatesScheduledDealWithFullStock() {
        authorize(UserRole.SELLER, 10L);
        when(deals.save(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.createDeal(10L, request(700, 10, 2, NOW.plusHours(1), NOW.plusHours(2)));
        assertThat(response.remainingStock()).isEqualTo(10);
        verify(deals).save(argThat(d -> d.getStatus() == Deal.DealStatus.SCHEDULED && d.getTotalStock() == 10));
    }

    @Test
    void adminCanCreateDealForAnotherSellersProduct() {
        authorize(UserRole.ADMIN, 99L);
        when(deals.save(any())).thenAnswer(call -> call.getArgument(0));
        assertThatCode(() -> service.createDeal(99L, validRequest())).doesNotThrowAnyException();
    }

    @Test
    void missingOrInactiveProductIsRejected() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.empty());
        assertError(() -> service.createDeal(10L, validRequest()), PRODUCT_NOT_FOUND);
        product.deactivate(java.time.LocalDateTime.of(2030, 1, 1, 9, 0));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        assertError(() -> service.createDeal(10L, validRequest()), PRODUCT_NOT_FOUND);
    }

    @Test
    void otherSellerAndOrdinaryUserAreRejected() {
        authorize(UserRole.SELLER, 99L);
        assertError(() -> service.createDeal(99L, validRequest()), DEAL_ACCESS_DENIED);
        authorize(UserRole.USER, 10L);
        assertError(() -> service.createDeal(10L, validRequest()), DEAL_ACCESS_DENIED);
    }

    @Test
    void openingMustBeStrictlyFutureAndClosingStrictlyAfterOpening() {
        authorize(UserRole.SELLER, 10L);
        assertError(() -> service.createDeal(10L, request(700, 10, 2, NOW, NOW.plusHours(1))), DEAL_INVALID_PERIOD);
        assertError(() -> service.createDeal(10L, request(700, 10, 2, NOW.minusHours(1), NOW.plusHours(1))), DEAL_INVALID_PERIOD);
        assertError(() -> service.createDeal(10L, request(700, 10, 2, NOW.plusHours(1), NOW.plusHours(1))), DEAL_INVALID_PERIOD);
        assertError(() -> service.createDeal(10L, request(700, 10, 2, NOW.plusHours(2), NOW.plusHours(1))), DEAL_INVALID_PERIOD);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1000, 1001})
    void priceMustBePositiveAndLessThanOriginalPrice(int price) {
        authorize(UserRole.SELLER, 10L);
        assertError(() -> service.createDeal(10L, request(price, 10, 2, NOW.plusHours(1), NOW.plusHours(2))), DEAL_INVALID_PRICE);
    }

    @Test
    void purchaseLimitCannotExceedStock() {
        authorize(UserRole.SELLER, 10L);
        assertError(() -> service.createDeal(10L, request(700, 10, 11, NOW.plusHours(1), NOW.plusHours(2))), DEAL_INVALID_STOCK);
    }

    @Test
    void overlappingPeriodIsRejected() {
        authorize(UserRole.SELLER, 10L);
        when(deals.existsOverlapping(eq(1L), isNull(), anyList(), any(), any())).thenReturn(true);
        assertError(() -> service.createDeal(10L, validRequest()), DEAL_PERIOD_OVERLAP);
        verify(deals, never()).save(any());
    }

    @Test
    void patchMergesNullFieldsAndExcludesItselfFromOverlapCheck() {
        Deal deal = editableDeal();
        var response = service.updateDeal(5L, 10L, new DealUpdateRequest(null, 20, null, null, null, null));
        assertThat(response.dealPrice()).isEqualByComparingTo("700");
        assertThat(response.totalStock()).isEqualTo(20);
        assertThat(response.remainingStock()).isEqualTo(20);
        assertThat(response.openAt()).isEqualTo(NOW.plusHours(1));
        assertThat(deal.getProductId()).isEqualTo(1L);
        verify(deals).existsOverlapping(eq(1L), eq(5L), anyList(), eq(NOW.plusHours(1)), eq(NOW.plusHours(2)));
        verify(deals).flush();
    }

    @Test
    void patchRevalidatesMergedPriceStockAndPeriod() {
        editableDeal();
        assertError(() -> service.updateDeal(5L, 10L, new DealUpdateRequest(BigDecimal.valueOf(1000), null, null, null, null, null)), DEAL_INVALID_PRICE);
        assertError(() -> service.updateDeal(5L, 10L, new DealUpdateRequest(null, 1, null, null, null, null)), DEAL_INVALID_STOCK);
        assertError(() -> service.updateDeal(5L, 10L, new DealUpdateRequest(null, null, null, NOW, null, null)), DEAL_INVALID_PERIOD);
        assertError(() -> service.updateDeal(5L, 10L, new DealUpdateRequest(null, null, null, null, NOW.plusHours(1), null)), DEAL_INVALID_PERIOD);
    }

    @Test
    void patchRejectsOverlap() {
        editableDeal();
        when(deals.existsOverlapping(eq(1L), eq(5L), anyList(), any(), any())).thenReturn(true);
        assertError(() -> service.updateDeal(5L, 10L, emptyPatch()), DEAL_PERIOD_OVERLAP);
    }

    @Test
    void cancellationIsSoftAndCannotBeRepeated() {
        Deal deal = editableDeal();
        service.cancelDeal(5L, 10L);
        assertThat(deal.getStatus()).isEqualTo(Deal.DealStatus.CLOSED);
        verify(deals, never()).delete(any());
        assertError(() -> service.cancelDeal(5L, 10L), DEAL_NOT_MODIFIABLE);
    }

    @Test
    void delayedSchedulerDoesNotAllowPatchOrCancellationAtOpening() {
        Deal deal = editableDeal();
        ReflectionTestUtils.setField(deal, "openAt", NOW);
        assertError(() -> service.updateDeal(5L, 10L, emptyPatch()), DEAL_NOT_MODIFIABLE);
        assertError(() -> service.cancelDeal(5L, 10L), DEAL_NOT_MODIFIABLE);
    }

    @ParameterizedTest
    @EnumSource(value = Deal.DealStatus.class, names = {"OPEN", "SOLD_OUT", "CLOSED"})
    void onlyScheduledDealsAreModifiable(Deal.DealStatus status) {
        Deal deal = editableDeal();
        ReflectionTestUtils.setField(deal, "status", status);
        assertError(() -> service.updateDeal(5L, 10L, emptyPatch()), DEAL_NOT_MODIFIABLE);
        assertError(() -> service.cancelDeal(5L, 10L), DEAL_NOT_MODIFIABLE);
    }

    @Test
    void patchAndCancellationTranslateOptimisticLockFailure() {
        editableDeal();
        doThrow(new ObjectOptimisticLockingFailureException(Deal.class, 5L)).when(deals).flush();
        assertError(() -> service.updateDeal(5L, 10L, emptyPatch()), DEAL_NOT_MODIFIABLE);
        assertError(() -> service.cancelDeal(5L, 10L), DEAL_NOT_MODIFIABLE);
    }

    @Test
    void missingDealIsReportedForBothWriteOperations() {
        assertError(() -> service.updateDeal(5L, 10L, emptyPatch()), DEAL_NOT_FOUND);
        assertError(() -> service.cancelDeal(5L, 10L), DEAL_NOT_FOUND);
    }

    private Deal editableDeal() {
        authorize(UserRole.SELLER, 10L);
        Deal deal = Deal.builder().productId(1L).dealPrice(BigDecimal.valueOf(700))
                .totalStock(10).maxPurchasePerUser(2).openAt(NOW.plusHours(1))
                .closeAt(NOW.plusHours(2)).holdDurationMinutes(10).build();
        ReflectionTestUtils.setField(deal, "id", 5L);
        when(deals.findById(5L)).thenReturn(Optional.of(deal));
        return deal;
    }

    private DealUpdateRequest emptyPatch() {
        return new DealUpdateRequest(null, null, null, null, null, null);
    }

    private void authorize(UserRole role, Long id) {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        User user = mock(User.class);
        when(user.getRole()).thenReturn(role);
        when(users.findById(id)).thenReturn(Optional.of(user));
    }

    private DealCreateRequest validRequest() {
        return request(700, 10, 2, NOW.plusHours(1), NOW.plusHours(2));
    }

    private DealCreateRequest request(int price, int stock, int limit, LocalDateTime open, LocalDateTime close) {
        return new DealCreateRequest(1L, BigDecimal.valueOf(price), stock, limit, open, close, 10);
    }

    private void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
