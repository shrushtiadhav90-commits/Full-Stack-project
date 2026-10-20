package com.nexus.quiz.service;

import com.nexus.quiz.entity.RefreshToken;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.RefreshTokenRepository;
import com.nexus.quiz.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public String issueRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hashToken(token))
                .expiresAtUtc(Instant.now().plus(REFRESH_TOKEN_LIFETIME))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        ensureValid(refreshToken);
        return refreshToken;
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hashToken(token))
                .filter(refreshToken -> !Boolean.TRUE.equals(refreshToken.getRevoked()))
                .ifPresent(refreshToken -> {
                    refreshToken.setRevoked(true);
                    refreshTokenRepository.save(refreshToken);
                });
    }

    @Transactional
    public RotatedRefreshToken rotateRefreshToken(String token) {
        RefreshToken oldToken = refreshTokenRepository.findByTokenHashForUpdate(hashToken(token))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        ensureValid(oldToken);

        User user = userRepository.findById(oldToken.getUserId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        return new RotatedRefreshToken(issueRefreshToken(user.getId()), user.getId());
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtUtcBefore(Instant.now());
    }

    private void ensureValid(RefreshToken refreshToken) {
        if (Boolean.TRUE.equals(refreshToken.getRevoked())
                || !refreshToken.getExpiresAtUtc().isAfter(Instant.now())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record RotatedRefreshToken(String token, Long userId) {}
}
