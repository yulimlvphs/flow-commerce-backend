package com.yulim.flowcommerce.domain.seller.controller;


import com.yulim.flowcommerce.common.auth.annotation.Auth;
import com.yulim.flowcommerce.common.response.ApiResponse;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRequest;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationResponse;
import com.yulim.flowcommerce.domain.seller.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 일반 회원이 판매자 전환을 신청/조회하는 API.
 * 인증된 회원이면 누구나 호출 가능하다고 가정한다 (이미 SELLER/ADMIN인 경우는
 * 서비스 레이어에서 ALREADY_SELLER로 막는다 — 별도 role 체크를 시큐리티 단에 둘 필요는 없음).
 */
@RestController
@RequestMapping("/api/sellers/apply")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService sellerApplicationService;

    @PostMapping
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> apply(
            @Auth Long userId,
            @Valid @RequestBody SellerApplicationRequest request
    ) {
        SellerApplicationResponse response = sellerApplicationService.apply(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> getMyLatestApplication(
            @Auth Long userId
    ) {
        SellerApplicationResponse response = sellerApplicationService.getMyLatestApplication(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}