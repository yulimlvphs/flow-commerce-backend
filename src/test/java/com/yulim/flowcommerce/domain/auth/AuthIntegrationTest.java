package com.yulim.flowcommerce.domain.auth;

import com.jayway.jsonpath.JsonPath;
import com.yulim.flowcommerce.common.auth.entity.RefreshToken;
import com.yulim.flowcommerce.common.auth.repository.RefreshTokenRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "test-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupStoresNormalizedEmailAndEncodedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "USER@EXAMPLE.COM",
                                  "password": "test-password-1234"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.role").value("USER"));

        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCase() throws Exception {
        signup(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "USER@EXAMPLE.COM",
                                  "password": "another-password-1234"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_002"));
    }

    @Test
    void loginIssuesAccessToken() throws Exception {
        signup(EMAIL, PASSWORD);

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "USER@EXAMPLE.COM",
                                  "password": "test-password-1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andReturn();

        String response = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(response, "$.data.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("flow-commerce-test");
        assertThat(jwt.getAudience()).containsExactly("flow-commerce-api-test");
        assertThat(jwt.getClaimAsString("email")).isEqualTo(EMAIL);
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");

        Cookie refreshToken = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.isHttpOnly()).isTrue();
        assertThat(refreshToken.getSecure()).isTrue();
        assertThat(refreshToken.getPath()).isEqualTo("/api/auth");
        assertThat(refreshToken.getMaxAge()).isEqualTo(14 * 24 * 60 * 60);
        assertThat(refreshTokenRepository.findAll()).singleElement()
                .satisfies(saved -> {
                    assertThat(saved.getTokenHash()).isEqualTo(sha256(refreshToken.getValue()));
                    assertThat(saved.getTokenHash()).doesNotContain(refreshToken.getValue());
                    assertThat(saved.isRevoked()).isFalse();
                });
    }

    @Test
    void refreshRotatesTokenAndDetectsReuseAcrossTheWholeFamily() throws Exception {
        signup(EMAIL, PASSWORD);
        Cookie firstToken = loginAndGetRefreshToken();

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        Cookie secondToken = refreshResult.getResponse().getCookie("refreshToken");
        assertThat(secondToken).isNotNull();
        assertThat(secondToken.getValue()).isNotEqualTo(firstToken.getValue());
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        mockMvc.perform(post("/api/auth/refresh").cookie(firstToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));

        mockMvc.perform(post("/api/auth/refresh").cookie(secondToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
        assertThat(refreshTokenRepository.findAll())
                .allMatch(RefreshToken::isRevoked);
    }

    @Test
    void refreshRejectsMissingUnknownAndExpiredTokens() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "unknown-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));

        signup(EMAIL, PASSWORD);
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        String expiredRawToken = "expired-refresh-token";
        refreshTokenRepository.save(RefreshToken.issue(
                sha256(expiredRawToken),
                UUID.randomUUID().toString(),
                user.getId(),
                Instant.now().minusSeconds(1)
        ));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", expiredRawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        signup(EMAIL, PASSWORD);
        Cookie refreshToken = loginAndGetRefreshToken();

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .cookie(refreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn();

        Cookie clearedCookie = logoutResult.getResponse().getCookie("refreshToken");
        assertThat(clearedCookie).isNotNull();
        assertThat(clearedCookie.getMaxAge()).isZero();
        assertThat(refreshTokenRepository.findAll()).singleElement()
                .satisfies(saved -> assertThat(saved.isRevoked()).isTrue());

        mockMvc.perform(post("/api/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_004"));
    }

    @Test
    void logoutWithoutRefreshTokenCookieIsANoOpSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    void loginDoesNotRevealWhetherEmailOrPasswordWasWrong() throws Exception {
        signup(EMAIL, PASSWORD);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "wrong-password-1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_003"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@example.com",
                                  "password": "wrong-password-1234"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_003"));
    }

    @Test
    void invalidSignupRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void passwordLongerThanBcryptByteLimitIsRejectedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unicode-password@example.com",
                                  "password": "가가가가가가가가가가가가가가가가가가가가가가가가가"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    private void signup(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private Cookie loginAndGetRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("refreshToken");
    }

    private static String sha256(String value) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
