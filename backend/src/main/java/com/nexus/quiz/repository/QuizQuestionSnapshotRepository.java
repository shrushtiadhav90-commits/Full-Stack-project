package com.nexus.quiz.repository;

import com.nexus.quiz.entity.QuizQuestionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizQuestionSnapshotRepository
        extends JpaRepository<QuizQuestionSnapshot, Long> {

    boolean existsByImageUrl(String imageUrl);

    List<QuizQuestionSnapshot> findByQuizIdOrderByIdAsc(Long quizId);

    java.util.Optional<QuizQuestionSnapshot> findByQuizIdAndQuestionId(Long quizId, Long questionId);

    @Modifying
    @Query(value = """
            INSERT IGNORE INTO quiz_question_snapshots
                (quiz_id, question_id, question_type, snapshot_text, snapshot_options,
                 correct_option_key, marks, model_answer, explanation, image_url)
            VALUES
                (:quizId, :questionId, :questionType, :snapshotText,
                 CAST(:snapshotOptions AS JSON), :correctOptionKey, :marks,
                 :modelAnswer, :explanation, :imageUrl)
            """, nativeQuery = true)
    int insertIgnore(
            @Param("quizId") Long quizId,
            @Param("questionId") Long questionId,
            @Param("questionType") String questionType,
            @Param("snapshotText") String snapshotText,
            @Param("snapshotOptions") String snapshotOptions,
            @Param("correctOptionKey") String correctOptionKey,
            @Param("marks") Integer marks,
            @Param("modelAnswer") String modelAnswer,
            @Param("explanation") String explanation,
            @Param("imageUrl") String imageUrl);

    @Modifying
    @Query("delete from QuizQuestionSnapshot snapshot where snapshot.quiz.id = :quizId")
    int deleteAllByQuizId(@Param("quizId") Long quizId);
}
