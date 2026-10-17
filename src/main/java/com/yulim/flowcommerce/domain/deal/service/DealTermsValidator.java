package com.yulim.flowcommerce.domain.deal.service;

import com.yulim.flowcommerce.domain.deal.dto.DealTerms;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

/** 딜 조건(기간·가격·구매 제한)과 같은 상품의 다른 딜과의 기간 중복을 검증한다. */
@Component
@RequiredArgsConstructor
public class DealTermsValidator {

    private static final List<Deal.DealStatus> ACTIVE_STATUSES = List.of(
            Deal.DealStatus.SCHEDULED, Deal.DealStatus.OPEN, Deal.DealStatus.SOLD_OUT);

    private final DealRepository dealRepository;

    /** @param excludedDealId 기간 중복 검사에서 제외할 딜(수정 대상 자기 자신). 신규 생성이면 null */
    public void validate(Product product, Long excludedDealId, DealTerms terms, LocalDateTime now) {
        validatePeriod(terms, now);
        validatePrice(product, terms);
        validatePurchaseLimit(terms);
        validateNoOverlap(product, excludedDealId, terms);
    }

    private void validatePeriod(DealTerms terms, LocalDateTime now) {
        if (!terms.openAt().isAfter(now) || !terms.closeAt().isAfter(terms.openAt())) {
            throw new BusinessException(DEAL_INVALID_PERIOD, "오픈은 현재 이후, 종료는 오픈 이후여야 합니다.");
        }
    }

    private void validatePrice(Product product, DealTerms terms) {
        if (terms.dealPrice().signum() <= 0 || terms.dealPrice().compareTo(product.getOriginalPrice()) >= 0) {
            throw new BusinessException(DEAL_INVALID_PRICE, "딜 가격은 0보다 크고 상품 정가보다 작아야 합니다.");
        }
    }

    private void validatePurchaseLimit(DealTerms terms) {
        if (terms.maxPurchasePerUser() > terms.totalStock()) {
            throw new BusinessException(DEAL_INVALID_STOCK, "1인 구매 제한은 총 재고를 초과할 수 없습니다.");
        }
    }

    private void validateNoOverlap(Product product, Long excludedDealId, DealTerms terms) {
        boolean overlaps = dealRepository.existsOverlapping(
                product.getId(), excludedDealId, ACTIVE_STATUSES, terms.openAt(), terms.closeAt());
        if (overlaps) {
            throw new BusinessException(DEAL_PERIOD_OVERLAP, "같은 상품의 활성 딜과 기간이 겹칩니다. productId=" + product.getId());
        }
    }
}
