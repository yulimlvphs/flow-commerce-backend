package com.yulim.flowcommerce.domain.user.entity;

import com.yulim.flowcommerce.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_users_email",
                        columnNames = "email"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    private User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
    }

    public static User register(String email, String passwordHash) {
        return new User(email, passwordHash);
    }

    public boolean isSeller() {
        return this.role == UserRole.SELLER;
    }

    /**
     * 판매자 신청이 승인되었을 때만 호출된다 (SellerApplicationService에서 호출).
     * 이미 SELLER이거나 ADMIN인 경우는 신청 단계에서 걸러지므로,
     * 여기서는 USER -> SELLER 전이만 허용한다.
     */
    public void promoteToSeller() {
        if (this.role != UserRole.USER) {
            throw new IllegalStateException("USER 역할만 판매자로 전환할 수 있습니다. 현재 role=" + this.role);
        }
        this.role = UserRole.SELLER;
    }
}