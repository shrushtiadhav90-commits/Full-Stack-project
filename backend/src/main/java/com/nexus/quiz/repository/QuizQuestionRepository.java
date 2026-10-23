package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    java.util.List<QuizQuestion> findByQuizIdOrderBySortOrderAscIdAsc(Long quizId);

    void deleteAllByQuizId(Long quizId);
}
