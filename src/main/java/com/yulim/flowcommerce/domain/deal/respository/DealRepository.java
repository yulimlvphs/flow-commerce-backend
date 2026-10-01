package com.yulim.flowcommerce.domain.deal.respository;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DealRepository extends JpaRepository<Deal, Long> {

    boolean existsByProductIdAndStatusIn(Long productId, List<Deal.DealStatus> statuses);
}
