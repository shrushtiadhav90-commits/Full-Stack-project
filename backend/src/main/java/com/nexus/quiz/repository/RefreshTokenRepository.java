package com.nexus.quiz.repository;

import com.nexus.quiz.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.userId = :userId and token.revoked = false "
            + "and (:currentTokenHash is null or token.tokenHash <> :currentTokenHash)")
    int revokeOtherActiveTokens(
            @Param("userId") Long userId,
            @Param("currentTokenHash") String currentTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int deleteByExpiresAtUtcBefore(Instant instant);
}
