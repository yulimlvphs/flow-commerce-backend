package com.yulim.flowcommerce.domain.seller.controller;

import com.yulim.flowcommerce.common.auth.annotation.Auth;
import com.yulim.flowcommerce.common.response.ApiResponse;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRejectRequest;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationResponse;
import com.yulim.flowcommerce.domain.seller.entity.SellerApplication;
import com.yulim.flowcommerce.domain.seller.service.SellerApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*관리자가 판매자 전환 신청을 조회/승인/거절하는 API. */
@RestController
@RequestMapping("/api/admin/seller-applications")
@RequiredArgsConstructor
public class SellerApplicationAdminController {

    private final SellerApplicationService sellerApplicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<SellerApplicationResponse>>> getApplications(
            @RequestParam(defaultValue = "PENDING") SellerApplication.ApplicationStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<SellerApplicationResponse> response = sellerApplicationService.getApplications(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> approve(
            @Auth Long adminId,
            @PathVariable Long applicationId
    ) {
        SellerApplicationResponse response = sellerApplicationService.approve(applicationId, adminId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> reject(
            @Auth Long adminId,
            @PathVariable Long applicationId,
            @Valid @RequestBody SellerApplicationRejectRequest request
    ) {
        SellerApplicationResponse response = sellerApplicationService.reject(applicationId, adminId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
