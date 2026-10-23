package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    java.util.List<QuestionOption> findByQuestionIdOrderBySortOrderAscIdAsc(Long questionId);

    void deleteAllByQuestionId(Long questionId);
}
