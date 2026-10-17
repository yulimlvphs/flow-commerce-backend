package com.yulim.flowcommerce.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
    // 테스트는 고정 Clock으로 전이 서비스를 직접 호출해 비동기 실행과의 간섭을 막는다.
}
