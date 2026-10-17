package com.yulim.flowcommerce.domain.deal.scheduler;

import com.yulim.flowcommerce.domain.deal.service.DealTransitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DealStatusScheduler {

    private final DealTransitionService dealTransitionService;

    @Scheduled(fixedDelay = 10_000)
    public void transitionStatuses() {
        dealTransitionService.transitionStatuses();
    }
}
