package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizAttempt;
import com.nexus.quiz.entity.AttemptStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    boolean existsByQuizId(Long quizId);

    List<QuizAttempt> findByQuizIdAndStatusOrderByIdAsc(Long quizId, AttemptStatus status);
}
