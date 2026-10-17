package com.yulim.flowcommerce.domain.deal.service;

import com.yulim.flowcommerce.domain.deal.entity.Deal;
import com.yulim.flowcommerce.domain.deal.repository.DealRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** 시각이 지난 딜의 저장 상태를 일괄 전환한다. 오픈 시각 도래 시 SCHEDULED→OPEN, 종료 시각 경과 시 활성 딜→CLOSED. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealTransitionService {

    private static final List<Deal.DealStatus> ACTIVE_STATUSES = List.of(
            Deal.DealStatus.SCHEDULED, Deal.DealStatus.OPEN, Deal.DealStatus.SOLD_OUT);

    private final DealRepository dealRepository;
    private final Clock clock;

    @Transactional
    public int transitionStatuses() {
        LocalDateTime now = LocalDateTime.now(clock);
        Instant instant = now.atZone(clock.getZone()).toInstant();
        int opened = dealRepository.openScheduled(now, instant);
        int closed = dealRepository.closeExpired(now, instant, ACTIVE_STATUSES);
        return opened + closed;
    }
}
