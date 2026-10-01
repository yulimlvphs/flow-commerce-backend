package com.yulim.flowcommerce.domain.seller.repository;

import com.yulim.flowcommerce.domain.seller.entity.SellerApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, SellerApplication.ApplicationStatus status);

    boolean existsByBusinessRegistrationNumberAndStatusIn(
            String businessRegistrationNumber, java.util.List<SellerApplication.ApplicationStatus> statuses);

    Optional<SellerApplication> findTopByUserIdOrderByAppliedAtDesc(Long userId);

    Page<SellerApplication> findAllByStatus(SellerApplication.ApplicationStatus status, Pageable pageable);
}
