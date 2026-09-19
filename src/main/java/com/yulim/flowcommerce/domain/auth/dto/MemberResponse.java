package com.yulim.flowcommerce.domain.auth.dto;

import com.yulim.flowcommerce.domain.member.entity.Member;
import com.yulim.flowcommerce.domain.member.entity.MemberRole;

public record MemberResponse(
        Long id,
        String email,
        MemberRole role
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getRole()
        );
    }
}
