package com.yulim.flowcommerce.common.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked; // 이 토큰이 이미 폐기됐는지를 표시

    private RefreshToken(String tokenHash, String familyId, Long userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken issue(
            String tokenHash,
            String familyId,
            Long userId,
            Instant expiresAt
    ) {
        return new RefreshToken(tokenHash, familyId, userId, expiresAt);
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
