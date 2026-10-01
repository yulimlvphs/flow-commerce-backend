package com.yulim.flowcommerce.domain.product.controller;

import com.yulim.flowcommerce.common.auth.annotation.Auth;
import com.yulim.flowcommerce.domain.product.dto.ProductCreateRequest;
import com.yulim.flowcommerce.domain.product.dto.ProductResponse;
import com.yulim.flowcommerce.domain.product.dto.ProductUpdateRequest;
import com.yulim.flowcommerce.domain.product.service.ProductService;
import com.yulim.flowcommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // SELLER 권한은 SecurityFilterChain에서 검사하고, 판매자 ID는 검증된 JWT의 sub에서 가져온다.
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Auth Long sellerId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductResponse response = productService.createProduct(sellerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<ProductResponse> response = productService.getProducts(pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable Long productId
    ) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @Auth Long sellerId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductResponse response = productService.updateProduct(productId, sellerId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @Auth Long sellerId,
            @PathVariable Long productId
    ) {
        productService.deleteProduct(productId, sellerId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
