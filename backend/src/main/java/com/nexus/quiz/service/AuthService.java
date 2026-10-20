package com.nexus.quiz.service;

import com.nexus.quiz.dto.auth.AuthTokenResponse;
import com.nexus.quiz.dto.auth.AuthUserResponse;
import com.nexus.quiz.dto.auth.ChangePasswordRequest;
import com.nexus.quiz.dto.auth.LoginRequest;
import com.nexus.quiz.dto.auth.UserProfileResponse;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.UserRepository;
import com.nexus.quiz.security.JwtService;
import com.nexus.quiz.util.DateTimeUtil;
import com.nexus.quiz.util.PasswordUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserRepository userRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthSession login(LoginRequest request) {
        User user = userRepository.findByUsernameOrEmailForUpdate(request.usernameOrEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Instant now = Instant.now();
        if (user.getLockedUntilUtc() != null && user.getLockedUntilUtc().isAfter(now)) {
            throw lockedAccount(user.getLockedUntilUtc());
        }
        if (user.getLockedUntilUtc() != null) {
            user.setLockedUntilUtc(null);
            user.setFailedLoginCount(0);
        }

        if (!PasswordUtil.verify(request.password(), user.getPasswordHash())) {
            int failedAttempts = user.getFailedLoginCount() + 1;
            user.setFailedLoginCount(failedAttempts);
            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                Instant lockedUntil = now.plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES);
                user.setLockedUntilUtc(lockedUntil);
            }
            userRepository.save(user);
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginCount(0);
        user.setLockedUntilUtc(null);
        userRepository.save(user);
        return issueSession(user);
    }

    @Transactional
    public AuthSession refresh(String refreshToken) {
        RefreshTokenService.RotatedRefreshToken rotated =
                refreshTokenService.rotateRefreshToken(refreshToken);
        User user = userRepository.findById(rotated.userId())
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        return new AuthSession(
                new AuthTokenResponse(
                        jwtService.generateAccessToken(user),
                        user.getRole(),
                        Boolean.TRUE.equals(user.getMustChangePassword()),
                        toAuthUser(user)),
                rotated.token());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.revokeRefreshToken(refreshToken);
    }

    @Transactional
    public void changePassword(
            Long userId,
            ChangePasswordRequest request,
            String currentRefreshToken) {
        User user = userRepository.findById(userId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));

        if (!PasswordUtil.verify(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(PasswordUtil.hash(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenService.revokeOtherRefreshTokens(userId, currentRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new BadCredentialsException("Authentication required"));
        return new UserProfileResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    private AuthSession issueSession(User user) {
        String refreshToken = refreshTokenService.issueRefreshToken(user.getId());
        AuthTokenResponse response = new AuthTokenResponse(
                jwtService.generateAccessToken(user),
                user.getRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                toAuthUser(user));
        return new AuthSession(response, refreshToken);
    }

    private AuthUserResponse toAuthUser(User user) {
        return new AuthUserResponse(user.getId(), user.getFullName(), user.getUsername());
    }

    private BadCredentialsException lockedAccount(Instant lockedUntil) {
        return new BadCredentialsException(
                "Account is locked until " + DateTimeUtil.toIstString(lockedUntil) + " IST");
    }

    public record AuthSession(AuthTokenResponse response, String refreshToken) {
    }
}
