package com.yulim.flowcommerce.domain.deal.service;

import com.yulim.flowcommerce.domain.deal.dto.DealCreateRequest;
import com.yulim.flowcommerce.domain.deal.dto.DealResponse;
import com.yulim.flowcommerce.domain.deal.dto.DealTerms;
import com.yulim.flowcommerce.domain.deal.dto.DealUpdateRequest;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

/** 딜 생성·수정·취소를 처리하는 명령(쓰기) 서비스. */
@Service
@RequiredArgsConstructor
// 상품 잠금 획득 후 최신 커밋을 읽어, 동시에 들어온 기간 중복 요청도 검증한다.
@Transactional(isolation = Isolation.READ_COMMITTED)
public class DealService {

    private final DealRepository dealRepository;
    private final ProductRepository productRepository;
    private final DealAccessPolicy accessPolicy;
    private final DealTermsValidator termsValidator;
    private final Clock clock;

    public DealResponse createDeal(Long userId, DealCreateRequest request) {
        Product product = lockActiveProduct(request.productId());
        accessPolicy.checkCanManage(product, userId);

        LocalDateTime now = LocalDateTime.now(clock);
        DealTerms terms = request.toTerms();
        termsValidator.validate(product, null, terms, now);

        Deal saved = dealRepository.save(Deal.create(request.productId(), terms));
        return DealResponse.from(saved, product, now);
    }

    public DealResponse updateDeal(Long dealId, Long userId, DealUpdateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        ManagedDeal target = loadModifiableDeal(dealId, userId, now);

        DealTerms terms = request.mergeInto(target.deal().terms());
        termsValidator.validate(target.product(), dealId, terms, now);
        target.deal().update(terms, now);

        flushOrReportConflict(dealId);
        return DealResponse.from(target.deal(), target.product(), now);
    }

    public void cancelDeal(Long dealId, Long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        ManagedDeal target = loadModifiableDeal(dealId, userId, now);

        target.deal().cancel(now);
        flushOrReportConflict(dealId);
    }

    private ManagedDeal loadModifiableDeal(Long dealId, Long userId, LocalDateTime now) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessException(DEAL_NOT_FOUND, "딜을 찾을 수 없습니다. dealId=" + dealId));
        Product product = lockActiveProduct(deal.getProductId());
        accessPolicy.checkCanManage(product, userId);

        if (!deal.isModifiable(now)) {
            throw new BusinessException(DEAL_NOT_MODIFIABLE, "시작 전 예정된 딜만 수정하거나 취소할 수 있습니다. dealId=" + dealId);
        }
        return new ManagedDeal(deal, product);
    }

    private Product lockActiveProduct(Long productId) {
        return productRepository.findByIdForUpdate(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND, "활성 상품을 찾을 수 없습니다. productId=" + productId));
    }

    // 커밋 시점까지 지연하면 낙관적 락 예외를 변환할 수 없어 이 트랜잭션 안에서 flush 한다.
    private void flushOrReportConflict(Long dealId) {
        try {
            dealRepository.flush();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new BusinessException(DEAL_NOT_MODIFIABLE, "다른 요청이 딜을 변경했습니다. 새로 조회해 주세요. dealId=" + dealId);
        }
    }

    private record ManagedDeal(Deal deal, Product product) {
    }
}
