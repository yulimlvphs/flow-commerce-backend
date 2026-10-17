package com.yulim.flowcommerce.domain.deal.repository;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    boolean existsByProductIdAndStatusIn(Long productId, List<Deal.DealStatus> statuses);

    @Query("""
            select (count(d) > 0) from Deal d
            where d.productId = :productId and d.status in :statuses
              and (:excludedId is null or d.id <> :excludedId)
              and d.openAt < :closeAt and d.closeAt > :openAt
            """)
    boolean existsOverlapping(Long productId, Long excludedId, List<Deal.DealStatus> statuses,
                              LocalDateTime openAt, LocalDateTime closeAt);

    @Query("""
            select d from Deal d
            where d.status <> com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.CLOSED
              and d.closeAt > :now
              and exists (select p.id from Product p where p.id = d.productId and p.status = :productStatus)
              and (:filter is null
                or (:filter = 'UPCOMING' and d.openAt > :now)
                or (:filter = 'ON_SALE' and d.openAt <= :now and d.remainingStock > 0)
                or (:filter = 'SOLD_OUT' and d.openAt <= :now and d.remainingStock = 0))
            """)
    Page<Deal> findVisible(LocalDateTime now, String filter, Product.ProductStatus productStatus, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Deal d set d.status = com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.OPEN,
                d.version = d.version + 1, d.updatedAt = :updatedAt
            where d.status = com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.SCHEDULED
              and d.openAt <= :now and d.closeAt > :now
            """)
    int openScheduled(LocalDateTime now, Instant updatedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Deal d set d.status = com.yulim.flowcommerce.domain.deal.entity.Deal.DealStatus.CLOSED,
                d.version = d.version + 1, d.updatedAt = :updatedAt
            where d.status in :statuses and d.closeAt <= :now
            """)
    int closeExpired(LocalDateTime now, Instant updatedAt, List<Deal.DealStatus> statuses);
}
