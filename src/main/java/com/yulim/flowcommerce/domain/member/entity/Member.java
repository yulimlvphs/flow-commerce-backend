package com.yulim.flowcommerce.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
        name = "members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_members_email",
                        columnNames = "email"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Member(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = MemberRole.USER;
    }

    public static Member register(String email, String passwordHash) {
        return new Member(email, passwordHash);
    }

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isSeller() {
        return this.role == MemberRole.SELLER;
    }

    /**
     * 판매자 신청이 승인되었을 때만 호출된다 (SellerApplicationService에서 호출).
     * 이미 SELLER이거나 ADMIN인 경우는 신청 단계에서 걸러지므로,
     * 여기서는 USER -> SELLER 전이만 허용한다.
     */
    public void promoteToSeller() {
        if (this.role != MemberRole.USER) {
            throw new IllegalStateException("USER 역할만 판매자로 전환할 수 있습니다. 현재 role=" + this.role);
        }
        this.role = MemberRole.SELLER;
    }
}