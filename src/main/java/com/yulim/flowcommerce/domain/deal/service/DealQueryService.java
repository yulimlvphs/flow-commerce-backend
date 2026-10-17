package com.yulim.flowcommerce.domain.deal.service;

import com.yulim.flowcommerce.domain.deal.dto.DealResponse;
import com.yulim.flowcommerce.domain.deal.dto.DealSearchStatus;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

/** 딜 목록·상세를 조회하는 읽기 전용 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final DealRepository dealRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public Page<DealResponse> getDeals(DealSearchStatus status, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        Pageable safePageable = toSafePageable(status, pageable);
        Page<Deal> page = dealRepository.findVisible(
                now, status == null ? null : status.name(), Product.ProductStatus.ACTIVE, safePageable);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), safePageable, page.getTotalElements());
        }

        Map<Long, Product> productsById = loadProducts(page);
        return page.map(deal -> {
            Product product = productsById.get(deal.getProductId());
            if (product == null) {
                throw new BusinessException(PRODUCT_NOT_FOUND, "딜 상품을 찾을 수 없습니다. productId=" + deal.getProductId());
            }
            return DealResponse.from(deal, product, now);
        });
    }

    public DealResponse getDeal(Long dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new BusinessException(DEAL_NOT_FOUND, "딜을 찾을 수 없습니다. dealId=" + dealId));
        Product product = productRepository.findById(deal.getProductId())
                .filter(Product::isActive)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND, "활성 상품을 찾을 수 없습니다. productId=" + deal.getProductId()));
        return DealResponse.from(deal, product, LocalDateTime.now(clock));
    }

    private Map<Long, Product> loadProducts(Page<Deal> deals) {
        List<Long> productIds = deals.stream()
                .map(Deal::getProductId)
                .distinct()
                .toList();
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private Pageable toSafePageable(DealSearchStatus status, Pageable pageable) {
        String sortProperty = status == DealSearchStatus.UPCOMING ? "openAt" : "closeAt";
        Sort sort = Sort.by(sortProperty).ascending().and(Sort.by("id").ascending());
        return PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), sort);
    }
}
