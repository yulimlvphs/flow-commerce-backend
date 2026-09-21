package com.yulim.flowcommerce.domain.auth.dto;

public record TokenResponse(
        String accessToken,
        long expiresIn // 클라이언트가 만료 전에 미리 갱신할 수 있는 능동형 전략의 근거
) {
}
