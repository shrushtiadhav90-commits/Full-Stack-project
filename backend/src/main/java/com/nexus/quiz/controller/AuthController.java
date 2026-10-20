package com.nexus.quiz.controller;

import com.nexus.quiz.dto.ApiResponse;
import com.nexus.quiz.dto.auth.AuthTokenResponse;
import com.nexus.quiz.dto.auth.ChangePasswordRequest;
import com.nexus.quiz.dto.auth.LoginRequest;
import com.nexus.quiz.dto.auth.UserProfileResponse;
import com.nexus.quiz.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final long refreshCookieMaxAgeSeconds;
    private final boolean secureCookie;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            @Value("${app.jwt.refresh-expiry-days:7}") long refreshExpiryDays,
            @Value("${app.cookie.secure:true}") boolean secureCookie,
            @Value("${app.cookie.same-site:Strict}") String cookieSameSite) {
        this.authService = authService;
        if (refreshExpiryDays <= 0) {
            throw new IllegalArgumentException("JWT_REFRESH_EXPIRY_DAYS must be positive");
        }
        this.refreshCookieMaxAgeSeconds = Duration.ofDays(refreshExpiryDays).toSeconds();
        this.secureCookie = secureCookie;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthSession session = authService.login(request);
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        AuthService.AuthSession session = authService.refresh(refreshToken);
        return withRefreshCookie(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .body(ApiResponse.success("Logout successful", null));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Long userId,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userId, request, refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Current user", authService.me(userId)));
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> withRefreshCookie(
            AuthService.AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(session.refreshToken(), Duration.ofSeconds(refreshCookieMaxAgeSeconds))
                                .toString())
                .body(ApiResponse.success("Authentication successful", session.response()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(cookieSameSite)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
