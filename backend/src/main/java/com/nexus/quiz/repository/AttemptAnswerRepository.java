package com.nexus.quiz.repository;

import com.nexus.quiz.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {
}
