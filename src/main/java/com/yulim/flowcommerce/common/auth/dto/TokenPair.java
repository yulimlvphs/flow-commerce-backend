package com.yulim.flowcommerce.common.auth.dto;

public record TokenPair(
        TokenResponse tokenResponse,
        String refreshToken
) {
}
