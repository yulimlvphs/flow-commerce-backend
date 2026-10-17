package com.yulim.flowcommerce.domain.deal.controller;

import com.yulim.flowcommerce.common.auth.annotation.Auth;
import com.yulim.flowcommerce.common.response.ApiResponse;
import com.yulim.flowcommerce.domain.deal.dto.DealCreateRequest;
import com.yulim.flowcommerce.domain.deal.dto.DealResponse;
import com.yulim.flowcommerce.domain.deal.dto.DealSearchStatus;
import com.yulim.flowcommerce.domain.deal.dto.DealUpdateRequest;
import com.yulim.flowcommerce.domain.deal.service.DealQueryService;
import com.yulim.flowcommerce.domain.deal.service.DealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
public class DealController {

    private final DealService dealService;
    private final DealQueryService dealQueryService;

    @PostMapping
    public ResponseEntity<ApiResponse<DealResponse>> createDeal(
            @Auth Long userId,
            @Valid @RequestBody DealCreateRequest request
    ) {
        DealResponse response = dealService.createDeal(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DealResponse>>> getDeals(
            @RequestParam(required = false) DealSearchStatus status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<DealResponse> response = dealQueryService.getDeals(status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{dealId}")
    public ResponseEntity<ApiResponse<DealResponse>> getDeal(
            @PathVariable Long dealId
    ) {
        DealResponse response = dealQueryService.getDeal(dealId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{dealId}")
    public ResponseEntity<ApiResponse<DealResponse>> updateDeal(
            @Auth Long userId,
            @PathVariable Long dealId,
            @Valid @RequestBody DealUpdateRequest request
    ) {
        DealResponse response = dealService.updateDeal(dealId, userId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{dealId}")
    public ResponseEntity<ApiResponse<Void>> cancelDeal(
            @Auth Long userId,
            @PathVariable Long dealId
    ) {
        dealService.cancelDeal(dealId, userId);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
