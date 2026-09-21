package com.yulim.flowcommerce.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank @Size(min = 32) String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl
) {
    public JwtProperties {
        if (accessTokenTtl != null
                && accessTokenTtl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException(
                    "JWT 유효기간은 1초 이상이어야 합니다."
            );
        }
        if (refreshTokenTtl != null
                && refreshTokenTtl.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException(
                    "리프레시 토큰 유효기간은 1초 이상이어야 합니다."
            );
        }
    }
}
