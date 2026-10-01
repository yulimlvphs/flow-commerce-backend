package com.yulim.flowcommerce.domain.deal.respository;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query("select d from Deal d where exists (select p.id from Product p where p.id = d.productId and p.status = :status)")
    Page<Deal> findAllWithActiveProduct(@Param("status") Product.ProductStatus status, Pageable pageable);

    boolean existsByProductIdAndStatusIn(Long productId, List<Deal.DealStatus> statuses);
}
