package com.yulim.flowcommerce.domain.seller.service;

import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRejectRequest;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRequest;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationResponse;
import com.yulim.flowcommerce.domain.seller.entity.SellerApplication;
import com.yulim.flowcommerce.domain.seller.repository.SellerApplicationRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.yulim.flowcommerce.common.constant.ErrorCode.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerApplicationService {

    /* 심사가 아직 끝나지 않은 상태. 중복/신청 사업자 번호 체크에 사용 */
    private static final List<SellerApplication.ApplicationStatus> IN_PROGRESS_STATUSES =
            List.of(SellerApplication.ApplicationStatus.PENDING, SellerApplication.ApplicationStatus.APPROVED);

    private final SellerApplicationRepository sellerApplicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public SellerApplicationResponse apply(Long userId, SellerApplicationRequest request) {
        User user = findUserOrThrow(userId);

        if (user.isSeller()) {
            throw new BusinessException(ALREADY_SELLER, "이미 판매자 권한을 보유한 회원입니다. userId=" + userId);
        }

        boolean hasPendingApplication = sellerApplicationRepository
                .existsByUserIdAndStatus(userId, SellerApplication.ApplicationStatus.PENDING);
        if (hasPendingApplication) {
            throw new BusinessException(APPLICATION_ALREADY_PENDING, "이미 심사 중인 신청이 있습니다. userId=" + userId);
        }

        boolean duplicateBusinessNumber = sellerApplicationRepository
                .existsByBusinessRegistrationNumberAndStatusIn(
                        request.businessRegistrationNumber(), IN_PROGRESS_STATUSES);
        if (duplicateBusinessNumber) {
            throw new BusinessException(DUPLICATE_BUSINESS_NUMBER,  "이미 심사 중이거나 등록된 사업자등록번호입니다. businessRegistrationNumber="
                    + request.businessRegistrationNumber());
        }

        SellerApplication application = SellerApplication.builder()
                .userId(userId)
                .businessName(request.businessName())
                .businessRegistrationNumber(request.businessRegistrationNumber())
                .build();

        SellerApplication saved = sellerApplicationRepository.save(application);
        return SellerApplicationResponse.from(saved);
    }

    public SellerApplicationResponse getMyLatestApplication(Long userId) {
        SellerApplication application = sellerApplicationRepository
                .findTopByUserIdOrderByAppliedAtDesc(userId)
                .orElseThrow(() -> new BusinessException(APPLICATION_NOT_FOUND,  "신청 내역이 없습니다. userId=" + userId));
        return SellerApplicationResponse.from(application);
    }

    public Page<SellerApplicationResponse> getApplications(SellerApplication.ApplicationStatus status, Pageable pageable) {
        return sellerApplicationRepository.findAllByStatus(status, pageable)
                .map(SellerApplicationResponse::from);
    }

    @Transactional
    public SellerApplicationResponse approve(Long applicationId, Long adminId) {
        SellerApplication application = findApplicationOrThrow(applicationId);
        validatePending(application);

        User user = findUserOrThrow(application.getUserId());
        user.promoteToSeller();
        application.approve(adminId);

        return SellerApplicationResponse.from(application);
    }

    @Transactional
    public SellerApplicationResponse reject(Long applicationId, Long adminId, SellerApplicationRejectRequest request) {
        SellerApplication application = findApplicationOrThrow(applicationId);
        validatePending(application);

        application.reject(adminId, request.reason());
        return SellerApplicationResponse.from(application);
    }

    private SellerApplication findApplicationOrThrow(Long applicationId) {
        return sellerApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(APPLICATION_NOT_FOUND, "신청 내역을 찾을 수 없습니다. id=" + applicationId));
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(APPLICATION_NOT_FOUND, "회원을 찾을 수 없습니다. userId=" + userId));
    }

    /**
     * 승인/거절 직전에 서비스 레이어에서 먼저 상태를 확인해서 정상적인 BusinessException으로
     * 처리한다. SellerApplication.approve()/reject() 내부의 validatePending()은
     * (동시 요청 등으로) 이 체크를 통과한 뒤에도 상태가 바뀌어버린 경우를 막는 최후의 방어선이다.
     */
    private void validatePending(SellerApplication application) {
        if (application.getStatus() != SellerApplication.ApplicationStatus.PENDING) {
            throw new BusinessException(APPLICATION_ALREADY_PROCESSED, "이미 처리된 신청입니다. id=" + application.getId() + ", status=" + application.getStatus());
        }
    }
}
