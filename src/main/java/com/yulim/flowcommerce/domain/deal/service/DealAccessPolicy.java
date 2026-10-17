package com.yulim.flowcommerce.domain.deal.service;

import com.yulim.flowcommerce.domain.product.entity.Product;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.yulim.flowcommerce.common.constant.ErrorCode.DEAL_ACCESS_DENIED;

/** 딜을 관리할 수 있는 사용자(관리자 또는 해당 상품의 판매자)인지 판단한다. */
@Component
@RequiredArgsConstructor
public class DealAccessPolicy {

    private final UserRepository userRepository;

    public void checkCanManage(Product product, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(DEAL_ACCESS_DENIED, "딜을 관리할 회원을 찾을 수 없습니다. userId=" + userId));

        boolean isAdmin = user.getRole() == UserRole.ADMIN;
        boolean isProductOwner = user.getRole() == UserRole.SELLER && product.isOwnedBy(userId);
        if (!isAdmin && !isProductOwner) {
            throw new BusinessException(DEAL_ACCESS_DENIED, "본인 상품의 딜만 관리할 수 있습니다. productId=" + product.getId());
        }
    }
}
