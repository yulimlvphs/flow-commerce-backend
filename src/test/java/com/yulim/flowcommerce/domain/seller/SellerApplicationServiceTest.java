package com.yulim.flowcommerce.domain.seller;

import com.yulim.flowcommerce.common.constant.ErrorCode;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRequest;
import com.yulim.flowcommerce.domain.seller.dto.SellerApplicationRejectRequest;
import com.yulim.flowcommerce.domain.seller.entity.SellerApplication;
import com.yulim.flowcommerce.domain.seller.repository.SellerApplicationRepository;
import com.yulim.flowcommerce.domain.seller.service.SellerApplicationService;
import com.yulim.flowcommerce.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static com.yulim.flowcommerce.domain.seller.entity.SellerApplication.ApplicationStatus.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SellerApplicationServiceTest {
    @Autowired SellerApplicationService service;
    @Autowired UserRepository users;
    @Autowired SellerApplicationRepository applications;
    @Autowired EntityManager em;
    User user;

    @BeforeEach
    void setup() {
        user = newUser();
    }

    @Test
    void applicationIsPendingAndBelongsToRequester() {
        var response = service.apply(user.getId(), request("101-01-00001"));
        em.flush();
        em.clear();
        var saved = applications.findById(response.id()).orElseThrow();
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getStatus()).isEqualTo(PENDING);
        assertThat(saved.getAppliedAt()).isNotNull();
        assertThat(saved.getReviewerId()).isNull();
        assertThat(service.getMyLatestApplication(user.getId()).id()).isEqualTo(saved.getId());
    }

    @Test
    void pendingApplicationCannotBeSubmittedTwice() {
        service.apply(user.getId(), request("101-01-00002"));
        assertError(() -> service.apply(user.getId(), request("101-01-00003")), ErrorCode.APPLICATION_ALREADY_PENDING);
    }

    @Test
    void pendingAndApprovedBusinessNumbersCannotBeUsedByAnotherUser() {
        var first = service.apply(user.getId(), request("101-01-00004"));
        var other = newUser();
        assertError(() -> service.apply(other.getId(), request("101-01-00004")), ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        service.approve(first.id(), 900L);
        assertError(() -> service.apply(other.getId(), request("101-01-00004")), ErrorCode.DUPLICATE_BUSINESS_NUMBER);
    }

    @Test
    void approvalPersistsSellerRoleAndReviewer() {
        var response = service.apply(user.getId(), request("101-01-00005"));
        service.approve(response.id(), 900L);
        em.flush();
        em.clear();
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.SELLER);
        var saved = applications.findById(response.id()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(APPROVED);
        assertThat(saved.getReviewerId()).isEqualTo(900L);
        assertThat(saved.getReviewedAt()).isNotNull();
        assertError(() -> service.apply(user.getId(), request("101-01-00006")), ErrorCode.ALREADY_SELLER);
    }

    @Test
    void rejectionKeepsUserRoleAndAllowsResubmission() {
        var first = service.apply(user.getId(), request("101-01-00007"));
        service.reject(first.id(), 900L, new SellerApplicationRejectRequest("서류 보완 필요"));
        em.flush();
        em.clear();
        var rejected = applications.findById(first.id()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("서류 보완 필요");
        assertThat(rejected.getReviewerId()).isEqualTo(900L);
        assertThat(rejected.getReviewedAt()).isNotNull();
        assertThat(users.findById(user.getId()).orElseThrow().getRole()).isEqualTo(UserRole.USER);
        var next = service.apply(user.getId(), request("101-01-00007"));
        assertThat(next.id()).isNotEqualTo(first.id());
        assertThat(service.getMyLatestApplication(user.getId()).id()).isEqualTo(next.id());
    }

    @Test
    void processedApplicationsCannotBeReviewedAgain() {
        var approved = service.apply(user.getId(), request("101-01-00008"));
        service.approve(approved.id(), 900L);
        assertProcessed(approved.id());
        var rejected = service.apply(newUser().getId(), request("101-01-00009"));
        service.reject(rejected.id(), 900L, new SellerApplicationRejectRequest("서류 보완"));
        assertProcessed(rejected.id());
    }

    @Test
    void missingApplicationReturnsBusinessError() {
        assertError(() -> service.getMyLatestApplication(user.getId()), ErrorCode.APPLICATION_NOT_FOUND);
        assertError(() -> service.approve(Long.MAX_VALUE, 900L), ErrorCode.APPLICATION_NOT_FOUND);
        assertError(() -> service.reject(Long.MAX_VALUE, 900L, new SellerApplicationRejectRequest("거절")), ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void statusFilterExcludesReviewedApplications() {
        var approved = service.apply(user.getId(), request("101-01-00010"));
        service.approve(approved.id(), 900L);
        var pending = service.apply(newUser().getId(), request("101-01-00011"));
        var page = service.getApplications(PENDING, PageRequest.of(0, 20));
        assertThat(page.getContent()).extracting("id").contains(pending.id()).doesNotContain(approved.id());
        assertThat(page.getContent()).allMatch(item -> item.status().equals("PENDING"));
    }

    private void assertProcessed(Long id) {
        assertError(() -> service.approve(id, 900L), ErrorCode.APPLICATION_ALREADY_PROCESSED);
        assertError(() -> service.reject(id, 900L, new SellerApplicationRejectRequest("거절")), ErrorCode.APPLICATION_ALREADY_PROCESSED);
    }

    private User newUser() {
        return users.save(User.register(UUID.randomUUID() + "@example.com", "unused-password-hash"));
    }

    private SellerApplicationRequest request(String number) {
        return new SellerApplicationRequest("테스트 상점", number);
    }

    private void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
