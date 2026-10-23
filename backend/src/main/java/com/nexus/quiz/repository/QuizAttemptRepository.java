package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizAttempt;
import com.nexus.quiz.entity.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuizId(Long quizId);

    List<QuizAttempt> findByQuizIdAndStatusOrderByIdAsc(Long quizId, AttemptStatus status);

    List<QuizAttempt> findByQuizIdOrderByIdAsc(Long quizId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from QuizAttempt attempt where attempt.id = :attemptId")
    Optional<QuizAttempt> findByIdForUpdate(@Param("attemptId") Long attemptId);

    @Modifying
    @Query("""
            update QuizAttempt attempt
            set attempt.status = com.nexus.quiz.entity.AttemptStatus.SUBMITTED,
                attempt.submittedAt = :submittedAt,
                attempt.timeTakenSeconds = :timeTakenSeconds,
                attempt.isAutoSubmitted = :autoSubmitted
            where attempt.id = :attemptId
              and attempt.status = com.nexus.quiz.entity.AttemptStatus.IN_PROGRESS
            """)
    int submitIfInProgress(
            @Param("attemptId") Long attemptId,
            @Param("submittedAt") Instant submittedAt,
            @Param("timeTakenSeconds") Integer timeTakenSeconds,
            @Param("autoSubmitted") boolean autoSubmitted);
}
