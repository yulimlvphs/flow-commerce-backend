package com.yulim.flowcommerce.domain.seller;

import com.jayway.jsonpath.JsonPath;
import com.yulim.flowcommerce.common.auth.service.JwtTokenService;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.entity.UserRole;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import com.yulim.flowcommerce.domain.seller.repository.SellerApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SellerApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SellerApplicationRepository applications;
    @Autowired JwtTokenService jwt;
    @Autowired PasswordEncoder passwords;
    User user;
    User admin;
    String userToken;
    String adminToken;
    static final String APPLY = "/api/sellers/apply";
    static final String ADMIN = "/api/admin/seller-applications";
    static final String BODY = """
            {"businessName":"테스트 상점","businessRegistrationNumber":"202-02-00001"}
            """;

    @BeforeEach
    void setup() {
        user = users.save(User.register(UUID.randomUUID() + "@example.com", passwords.encode("test-password-1234")));
        admin = User.register(UUID.randomUUID() + "@example.com", "unused-password-hash");
        ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
        admin = users.save(admin);
        userToken = token(user);
        adminToken = token(admin);
    }

    @Test
    void anonymousCannotApplyOrReadPrivateApplications() throws Exception {
        mvc.perform(post(APPLY).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(APPLY + "/me")).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN + "/1/approve")).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN + "/1/reject").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"거절\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userAndSellerCannotAccessAnyAdminEndpoint() throws Exception {
        String normalToken = userToken;
        user.promoteToSeller();
        for (String bearer : new String[]{normalToken, token(user)}) {
            mvc.perform(get(ADMIN).header("Authorization", bearer)).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/1/approve").header("Authorization", bearer)).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/1/reject").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"거절\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void approvalUsesJwtIdsAndNewLoginGrantsProductAccess() throws Exception {
        Long id = apply();
        mvc.perform(get(APPLY + "/me").header("Authorization", userToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get(APPLY + "/me").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(get(ADMIN).header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
        mvc.perform(post(ADMIN + "/" + id + "/approve").param("adminId", "999999")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(applications.findById(id).orElseThrow().getReviewerId()).isEqualTo(admin.getId());
        mvc.perform(post("/api/products").header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"상품\",\"originalPrice\":1000}"))
                .andExpect(status().isForbidden());
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"test-password-1234\"}"))
                .andExpect(status().isOk()).andReturn();
        String access = JsonPath.read(login.getResponse().getContentAsString(), "$.data.accessToken");
        mvc.perform(post("/api/products").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"상품\",\"originalPrice\":1000}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.sellerId").value(user.getId()));
    }

    @Test
    void rejectionValidatesReasonAndAllowsResubmission() throws Exception {
        Long id = apply();
        for (String reason : new String[]{" ", "a".repeat(501)}) {
            mvc.perform(post(ADMIN + "/" + id + "/reject").header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(ADMIN + "/" + id + "/reject").header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"서류 보완\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("서류 보완"));
        mvc.perform(post(ADMIN + "/" + id + "/approve").header("Authorization", adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELLER_004"));
        assertThat(apply()).isNotEqualTo(id);
    }

    @Test
    void applicationValidatesNameAndBusinessNumber() throws Exception {
        for (String body : new String[]{
                "{\"businessName\":\" \",\"businessRegistrationNumber\":\"202-02-00001\"}",
                "{\"businessName\":\"상점\",\"businessRegistrationNumber\":\"invalid\"}",
                "{\"businessName\":\"" + "a".repeat(101) + "\",\"businessRegistrationNumber\":\"202-02-00001\"}"}) {
            mvc.perform(post(APPLY).header("Authorization", userToken).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_001"));
        }
    }

    private Long apply() throws Exception {
        var result = mvc.perform(post(APPLY).header("Authorization", userToken).param("userId", "999999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String token(User user) {
        return "Bearer " + jwt.issue(user).accessToken();
    }
}
