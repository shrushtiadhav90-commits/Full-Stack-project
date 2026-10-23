package com.nexus.quiz.repository;

import com.nexus.quiz.entity.Quiz;
import com.nexus.quiz.entity.QuizStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface QuizRepository extends JpaRepository<Quiz, Long>,
        JpaSpecificationExecutor<Quiz> {

    List<Quiz> findByCreatedByIdOrderByCreatedAtDescIdDesc(Long facultyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select quiz from Quiz quiz where quiz.id = :quizId")
    Optional<Quiz> findByIdForUpdate(@Param("quizId") Long quizId);

    @Query("select quiz.id from Quiz quiz where quiz.status = :status and quiz.endTime <= :now")
    List<Long> findExpiredQuizIds(@Param("status") QuizStatus status, @Param("now") Instant now);

    @Modifying
    @Query("update Quiz quiz set quiz.status = com.nexus.quiz.entity.QuizStatus.CLOSED "
            + "where quiz.id = :quizId and quiz.status = com.nexus.quiz.entity.QuizStatus.SCHEDULED "
            + "and quiz.endTime <= :now")
    int closeIfExpired(@Param("quizId") Long quizId, @Param("now") Instant now);
}
