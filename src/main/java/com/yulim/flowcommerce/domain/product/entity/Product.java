package com.yulim.flowcommerce.domain.product.entity;

import com.yulim.flowcommerce.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private BigDecimal originalPrice;

    @Column(length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    private LocalDateTime deletedAt;

    @Builder
    private Product(Long sellerId, String name, String description, BigDecimal originalPrice, String thumbnailUrl) {
        this.sellerId = sellerId;
        this.name = name;
        this.description = description;
        this.originalPrice = originalPrice;
        this.thumbnailUrl = thumbnailUrl;
        this.status = ProductStatus.ACTIVE;
    }

    public void update(String name, String description, BigDecimal originalPrice, String thumbnailUrl) {
        if (name != null) {
            this.name = name;
        }
        if (description != null) {
            this.description = description;
        }
        if (originalPrice != null) {
            this.originalPrice = originalPrice;
        }
        if (thumbnailUrl != null) {
            this.thumbnailUrl = thumbnailUrl;
        }
    }

    /** 소프트 삭제. 물리 삭제를 하지 않는 이유는 Deal 등 참조 무결성 보호를 위해서다. */
    public void deactivate() {
        this.status = ProductStatus.INACTIVE;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) {
        return this.sellerId.equals(userId);
    }

    public boolean isActive() {
        return this.status == ProductStatus.ACTIVE;
    }

    public enum ProductStatus {
        ACTIVE, INACTIVE
    }
}
