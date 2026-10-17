package com.yulim.flowcommerce.common.entity;

import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BaseEntityIntegrationTest {

    @Autowired
    ProductRepository products;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistenceSetsBothTimestampsAndDirtyCheckingOnlyChangesUpdatedAt() {
        Instant beforeInsert = Instant.now();
        Product product = products.saveAndFlush(Product.builder()
                .sellerId(1L)
                .name("original")
                .originalPrice(BigDecimal.TEN)
                .build());
        assertThat(product.getCreatedAt()).isBetween(beforeInsert, Instant.now());
        assertThat(product.getUpdatedAt()).isEqualTo(product.getCreatedAt());

        Long id = product.getId();
        // 수정 시각을 과거로 고정해 sleep이나 DB 시각 정밀도에 의존하지 않는다.
        entityManager.createNativeQuery(
                "update products set updated_at = '2000-01-01 00:00:00' where id = :id")
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();

        Product saved = products.findById(id).orElseThrow();
        Instant createdAt = saved.getCreatedAt();
        Instant oldUpdatedAt = saved.getUpdatedAt();
        saved.update("changed", null, null, null);
        entityManager.flush();
        entityManager.clear();

        Product updated = products.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("changed");
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfter(oldUpdatedAt);

        Instant updatedAt = updated.getUpdatedAt();
        entityManager.flush();
        entityManager.clear();
        assertThat(products.findById(id).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
    }
}
