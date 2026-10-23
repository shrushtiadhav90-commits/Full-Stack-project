package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizQuestionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionSnapshotRepository
        extends JpaRepository<QuizQuestionSnapshot, Long> {
}
