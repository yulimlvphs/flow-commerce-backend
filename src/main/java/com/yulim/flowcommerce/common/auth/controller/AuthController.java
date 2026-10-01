package com.yulim.flowcommerce.common.auth.controller;

import com.yulim.flowcommerce.common.auth.dto.LoginRequest;
import com.yulim.flowcommerce.common.auth.dto.UserResponse;
import com.yulim.flowcommerce.common.auth.dto.SignupRequest;
import com.yulim.flowcommerce.common.auth.dto.TokenPair;
import com.yulim.flowcommerce.common.auth.dto.TokenResponse;
import com.yulim.flowcommerce.common.auth.service.AuthService;
import com.yulim.flowcommerce.global.config.JwtProperties;
import com.yulim.flowcommerce.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse user = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(user));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenPair tokens = authService.login(request);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.ok(tokens.tokenResponse());
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken,
            HttpServletResponse response
    ) {
        TokenPair tokens = authService.reissue(rawRefreshToken);
        setRefreshTokenCookie(response, tokens.refreshToken());
        return ApiResponse.ok(tokens.tokenResponse());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String rawRefreshToken,
            HttpServletResponse response
    ) {
        authService.logout(rawRefreshToken);
        clearRefreshTokenCookie(response);
        return ApiResponse.ok();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String rawRefreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
