package com.yulim.flowcommerce.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

/**
 * 엔티티의 생성·수정 시각을 UTC 기준으로 기록한다.
 * JPQL 벌크 UPDATE와 native SQL은 콜백을 거치지 않으므로 수정 시각을 직접 갱신해야 한다.
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity {

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
