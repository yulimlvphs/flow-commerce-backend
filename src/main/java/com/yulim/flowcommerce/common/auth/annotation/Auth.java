package com.yulim.flowcommerce.common.auth.annotation;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 JWT의 sub를 Long 회원 ID로 주입한다.
 * 인증 및 역할 검사는 SecurityFilterChain에서 처리한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)")
public @interface Auth {
}
