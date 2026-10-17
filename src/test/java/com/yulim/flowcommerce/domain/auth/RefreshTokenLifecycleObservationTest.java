package com.yulim.flowcommerce.domain.auth;

import com.jayway.jsonpath.JsonPath;
import com.yulim.flowcommerce.common.auth.entity.RefreshToken;
import com.yulim.flowcommerce.common.auth.repository.RefreshTokenRepository;
import com.yulim.flowcommerce.domain.user.entity.User;
import com.yulim.flowcommerce.domain.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입~로그인~리프레시 회전~재사용 탐지~로그아웃까지의 전 과정에서
 * access token(JWT) 클레임과 refresh_tokens 테이블(familyId, tokenHash, revoked)이
 * 실제로 어떻게 바뀌는지 콘솔에 출력해서 눈으로 확인하기 위한 테스트.
 * (IntelliJ에서 테스트를 실행하면 콘솔에 바로 출력되고, Gradle CLI에서는 `--info` 옵션이 필요하다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenLifecycleObservationTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "test-password-1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    class 로그인_시_토큰_발급 {

        @Test
        void 액세스_토큰과_리프레시_토큰이_발급되고_리프레시_토큰은_해시로만_저장된다() throws Exception {
            User user = signup(EMAIL, PASSWORD);
            MvcResult loginResult = login(EMAIL, PASSWORD);
            String accessToken = extractAccessToken(loginResult);
            Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

            printAccessTokenClaims("로그인 직후", accessToken);
            printRefreshCookie("로그인 직후", refreshCookie);
            printRefreshTokenTable("로그인 직후", user.getId());

            RefreshToken saved = refreshTokenRepository.findAll().get(0);
            assertThat(saved.getTokenHash()).isEqualTo(sha256(refreshCookie.getValue()));
            assertThat(saved.getTokenHash()).doesNotContain(refreshCookie.getValue());
            assertThat(saved.isRevoked()).isFalse();
        }
    }

    @Nested
    class 정상적인_리프레시_회전 {

        @Test
        void 재발급할때마다_토큰은_바뀌지만_familyId는_그대로_유지된다() throws Exception {
            User user = signup(EMAIL, PASSWORD);
            Cookie firstCookie = login(EMAIL, PASSWORD).getResponse().getCookie("refreshToken");
            printRefreshTokenTable("1세대 발급(로그인) 직후", user.getId());

            MvcResult firstRefreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie secondCookie = firstRefreshResult.getResponse().getCookie("refreshToken");
            printAccessTokenClaims("2세대 재발급 직후", extractAccessToken(firstRefreshResult));
            printRefreshCookie("2세대 재발급 직후", secondCookie);
            printRefreshTokenTable("2세대 재발급 직후 (1세대는 revoked=true로 바뀌어야 함)", user.getId());

            MvcResult secondRefreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(secondCookie))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie thirdCookie = secondRefreshResult.getResponse().getCookie("refreshToken");
            printRefreshTokenTable("3세대 재발급 직후 (1·2세대 모두 revoked=true)", user.getId());

            List<RefreshToken> generations = refreshTokenRepository.findAll().stream()
                    .sorted(Comparator.comparing(RefreshToken::getId))
                    .toList();

            assertThat(generations).hasSize(3);
            assertThat(generations).extracting(RefreshToken::getFamilyId).containsOnly(generations.get(0).getFamilyId());
            assertThat(generations.get(0).isRevoked()).isTrue();
            assertThat(generations.get(1).isRevoked()).isTrue();
            assertThat(generations.get(2).isRevoked()).isFalse();
            assertThat(List.of(firstCookie.getValue(), secondCookie.getValue(), thirdCookie.getValue()))
                    .doesNotHaveDuplicates();
        }
    }

    @Nested
    class 토큰_재사용_탐지 {

        @Test
        void 이미_사용된_1세대_토큰을_재사용하면_family_전체가_폐기된다() throws Exception {
            User user = signup(EMAIL, PASSWORD);
            Cookie firstCookie = login(EMAIL, PASSWORD).getResponse().getCookie("refreshToken");

            MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie secondCookie = refreshResult.getResponse().getCookie("refreshToken");
            printRefreshTokenTable("정상 재발급 직후 (공격자가 훔친 1세대 토큰은 아직 살아있는 것처럼 보임)", user.getId());

            mockMvc.perform(post("/api/auth/refresh").cookie(firstCookie))
                    .andExpect(status().isUnauthorized());
            printRefreshTokenTable("이미 폐기된 1세대 토큰 재사용 시도 이후 (family 전체 폐기)", user.getId());

            List<RefreshToken> generations = refreshTokenRepository.findAll();
            assertThat(generations).hasSize(2).allMatch(RefreshToken::isRevoked);

            mockMvc.perform(post("/api/auth/refresh").cookie(secondCookie))
                    .andExpect(status().isUnauthorized());
            printRefreshTokenTable("정상 발급됐던 2세대 토큰마저 이제 사용 불가", user.getId());
        }
    }

    @Nested
    class 로그아웃_시_토큰_폐기 {

        @Test
        void 로그아웃하면_현재_family가_폐기되고_쿠키가_삭제된다() throws Exception {
            User user = signup(EMAIL, PASSWORD);
            Cookie refreshCookie = login(EMAIL, PASSWORD).getResponse().getCookie("refreshToken");
            printRefreshTokenTable("로그아웃 전", user.getId());

            MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout").cookie(refreshCookie))
                    .andExpect(status().isOk())
                    .andReturn();
            Cookie clearedCookie = logoutResult.getResponse().getCookie("refreshToken");
            printRefreshCookie("로그아웃 직후 응답 쿠키 (Max-Age=0으로 삭제 지시)", clearedCookie);
            printRefreshTokenTable("로그아웃 직후", user.getId());

            assertThat(clearedCookie.getMaxAge()).isZero();
            assertThat(refreshTokenRepository.findAll()).singleElement()
                    .matches(RefreshToken::isRevoked);
        }
    }

    private User signup(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isCreated());
        return userRepository.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElseThrow();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String extractAccessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println("========== " + title + " ==========");
    }

    private void printAccessTokenClaims(String phase, String rawAccessToken) {
        printHeader(phase + " | Access Token(JWT) 클레임");
        Jwt jwt = jwtDecoder.decode(rawAccessToken);
        System.out.println("sub(userId) : " + jwt.getSubject());
        System.out.println("email         : " + jwt.getClaimAsString("email"));
        System.out.println("role          : " + jwt.getClaimAsString("role"));
        System.out.println("issuedAt      : " + jwt.getIssuedAt());
        System.out.println("expiresAt     : " + jwt.getExpiresAt());
    }

    private void printRefreshCookie(String phase, Cookie cookie) {
        printHeader(phase + " | Refresh Token 쿠키(raw)");
        if (cookie == null) {
            System.out.println("(쿠키 없음)");
            return;
        }
        String raw = cookie.getValue();
        String rawPreview = raw.isEmpty() ? "(빈 값)" : raw.substring(0, Math.min(16, raw.length())) + "...";
        System.out.println("raw(prefix)   : " + rawPreview + " (length=" + raw.length() + ")");
        if (!raw.isEmpty()) {
            System.out.println("hash(prefix)  : " + sha256(raw).substring(0, 16) + "...");
        }
        System.out.println("httpOnly      : " + cookie.isHttpOnly());
        System.out.println("secure        : " + cookie.getSecure());
        System.out.println("path          : " + cookie.getPath());
        System.out.println("maxAge(sec)   : " + cookie.getMaxAge());
    }

    private void printRefreshTokenTable(String phase, Long userId) {
        printHeader(phase + " | DB: refresh_tokens 테이블");
        List<RefreshToken> tokens = refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUserId().equals(userId))
                .sorted(Comparator.comparing(RefreshToken::getId))
                .toList();
        System.out.printf("%-4s %-38s %-20s %-8s %-30s%n", "id", "familyId", "tokenHash(prefix)", "revoked", "expiresAt");
        for (RefreshToken token : tokens) {
            System.out.printf("%-4d %-38s %-20s %-8s %-30s%n",
                    token.getId(),
                    token.getFamilyId(),
                    token.getTokenHash().substring(0, Math.min(16, token.getTokenHash().length())) + "...",
                    token.isRevoked(),
                    token.getExpiresAt());
        }
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
