package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizClassRepository extends JpaRepository<QuizClass, Long> {

    java.util.List<QuizClass> findByQuizIdOrderByIdAsc(Long quizId);

    java.util.List<QuizClass> findBySchoolClassId(Long classId);

    void deleteAllByQuizId(Long quizId);
}
