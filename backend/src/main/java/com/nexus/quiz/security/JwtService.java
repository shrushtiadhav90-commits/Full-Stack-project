package com.nexus.quiz.security;

import com.nexus.quiz.entity.Role;
import com.nexus.quiz.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(30);

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return generateAccessToken(user.getId(), user.getRole());
    }

    public String generateAccessToken(Long userId, Role role) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ACCESS_TOKEN_LIFETIME)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        return parseAccessToken(token).isPresent();
    }

    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            String roleClaim = claims.get("role", String.class);
            Date issuedAt = claims.getIssuedAt();
            Date expiration = claims.getExpiration();
            if (subject == null || roleClaim == null || issuedAt == null || expiration == null) {
                return Optional.empty();
            }

            long userId = Long.parseLong(subject);
            Role role = Role.valueOf(roleClaim);
            if (userId <= 0 || !expiration.after(issuedAt)) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(userId, role));
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(long userId, Role role) {}
}
