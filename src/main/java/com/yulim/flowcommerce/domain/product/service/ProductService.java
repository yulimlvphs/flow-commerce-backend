package com.yulim.flowcommerce.domain.product.service;
import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import com.yulim.flowcommerce.domain.product.dto.ProductCreateRequest;
import com.yulim.flowcommerce.domain.product.dto.ProductResponse;
import com.yulim.flowcommerce.domain.product.dto.ProductUpdateRequest;
import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static com.yulim.flowcommerce.common.constant.ErrorCode.PRODUCT_ACCESS_DENIED;
import static com.yulim.flowcommerce.common.constant.ErrorCode.PRODUCT_HAS_ACTIVE_DEAL;
import static com.yulim.flowcommerce.common.constant.ErrorCode.PRODUCT_NOT_FOUND;
import static com.yulim.flowcommerce.common.constant.ErrorCode.PRODUCT_PRICE_LOCKED;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    /** 목록 조회 시 한 번에 내려줄 수 있는 최대 페이지 크기. 무제한 조회로 인한 부하를 막기 위함. */
    private static final int MAX_PAGE_SIZE = 50;

    /** 품절도 종료 전 활성 딜이므로 삭제와 정가 변경을 제한한다. */
    private static final List<Deal.DealStatus> ACTIVE_DEAL_STATUSES =
            List.of(Deal.DealStatus.SCHEDULED, Deal.DealStatus.OPEN, Deal.DealStatus.SOLD_OUT);

    private final ProductRepository productRepository;
    private final DealRepository dealRepository;
    private final Clock clock;

    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductCreateRequest request) {
        Product product = request.toEntity(sellerId);
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    public Page<ProductResponse> getProducts(Pageable pageable) {
        Pageable safePageable = capPageSize(pageable);
        return productRepository
                .findAllByStatus(Product.ProductStatus.ACTIVE, safePageable)
                .map(ProductResponse::from);
    }

    public ProductResponse getProduct(Long productId) {
        Product product = findProductOrThrow(productId);
        return ProductResponse.from(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProductResponse updateProduct(Long productId, Long requesterId, ProductUpdateRequest request) {
        Product product = findProductForUpdate(productId);
        validateOwnership(product, requesterId);

        if (request.originalPrice() != null
                && request.originalPrice().compareTo(product.getOriginalPrice()) != 0
                && dealRepository.existsByProductIdAndStatusIn(productId, ACTIVE_DEAL_STATUSES)) {
            throw new BusinessException(PRODUCT_PRICE_LOCKED, "활성 딜이 있어 정가를 변경할 수 없습니다. productId=" + productId);
        }

        product.update(request.name(), request.description(),
                request.originalPrice(), request.thumbnailUrl());

        return ProductResponse.from(product);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteProduct(Long productId, Long requesterId) {
        Product product = findProductForUpdate(productId);
        validateOwnership(product, requesterId);

        boolean hasActiveDeal = dealRepository.existsByProductIdAndStatusIn(productId, ACTIVE_DEAL_STATUSES);
        if (hasActiveDeal) {
            throw new BusinessException(PRODUCT_HAS_ACTIVE_DEAL);
        }

        product.deactivate(LocalDateTime.now(clock));
    }

    private Product findProductForUpdate(Long productId) {
        return productRepository.findByIdForUpdate(productId).filter(Product::isActive)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND, "활성 상품을 찾을 수 없습니다. productId=" + productId));
    }

    private Product findProductOrThrow(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new BusinessException(PRODUCT_NOT_FOUND));
    }

    private void validateOwnership(Product product, Long requesterId) {
        if (!product.isOwnedBy(requesterId)) {
            throw new BusinessException(PRODUCT_ACCESS_DENIED);
        }
    }

    private Pageable capPageSize(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            return org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
