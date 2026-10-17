package com.yulim.flowcommerce.domain.product.repository;

import com.yulim.flowcommerce.domain.product.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    /** 같은 상품의 딜 생성·수정과 상품 가격 변경·삭제를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(Long id);

    Page<Product> findAllByStatus(Product.ProductStatus status, Pageable pageable);
}
