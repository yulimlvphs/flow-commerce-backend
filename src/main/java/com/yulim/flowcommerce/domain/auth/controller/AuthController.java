package com.yulim.flowcommerce.domain.auth.controller;

import com.yulim.flowcommerce.domain.auth.dto.LoginRequest;
import com.yulim.flowcommerce.domain.auth.dto.MemberResponse;
import com.yulim.flowcommerce.domain.auth.dto.SignupRequest;
import com.yulim.flowcommerce.domain.auth.dto.TokenResponse;
import com.yulim.flowcommerce.domain.auth.service.AuthService;
import com.yulim.flowcommerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(
            @Valid @RequestBody SignupRequest request
    ) {
        MemberResponse member = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(member));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ApiResponse.ok(authService.login(request));
    }
}
