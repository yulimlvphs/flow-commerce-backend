package com.yulim.flowcommerce.common.auth.dto;

import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;

public record UserResponse(
        Long id,
        String email,
        UserRole role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}
