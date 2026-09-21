package com.yulim.flowcommerce.domain.auth.service;

import com.yulim.flowcommerce.domain.auth.dto.LoginRequest;
import com.yulim.flowcommerce.domain.auth.dto.MemberResponse;
import com.yulim.flowcommerce.domain.auth.dto.SignupRequest;
import com.yulim.flowcommerce.domain.auth.dto.TokenPair;
import com.yulim.flowcommerce.domain.auth.dto.TokenResponse;
import com.yulim.flowcommerce.domain.auth.entity.RefreshToken;
import com.yulim.flowcommerce.domain.auth.repository.RefreshTokenRepository;
import com.yulim.flowcommerce.domain.member.entity.Member;
import com.yulim.flowcommerce.domain.member.repository.MemberRepository;
import com.yulim.flowcommerce.global.config.JwtProperties;
import com.yulim.flowcommerce.global.constant.ErrorCode;
import com.yulim.flowcommerce.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;

    @Transactional
    public MemberResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = Member.register(email, passwordEncoder.encode(request.password()));

        try {
            return MemberResponse.from(memberRepository.saveAndFlush(member));
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        try {
            authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (BadCredentialsException exception) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        String rawRefreshToken = generateRandomToken();
        refreshTokenRepository.save(RefreshToken.issue(
                sha256(rawRefreshToken),
                UUID.randomUUID().toString(),
                member.getId(),
                Instant.now().plus(jwtProperties.refreshTokenTtl())
        ));

        return new TokenPair(jwtTokenService.issue(member), rawRefreshToken);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenPair reissue(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken saved = refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (saved.isRevoked()) {
            refreshTokenRepository.revokeAllByFamilyId(saved.getFamilyId()); // 이 토큰과 같은 패밀리(같은 로그인에서 파생된 모든 토큰을 전부 폐기 처리해서 체인 전체를 끊어버린다
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }

        Instant now = Instant.now();
        if (saved.isExpired(now)) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(saved.getMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        saved.revoke();

        String newRawRefreshToken = generateRandomToken();
        refreshTokenRepository.save(RefreshToken.issue(
                sha256(newRawRefreshToken),
                saved.getFamilyId(), // 패밀리의 계승. 원래 있던 familyId를 그대로 물려받음.
                saved.getMemberId(),
                now.plus(jwtProperties.refreshTokenTtl())
        ));

        return new TokenPair(jwtTokenService.issue(member), newRawRefreshToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeAllByFamilyId(token.getFamilyId()));
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String generateRandomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
