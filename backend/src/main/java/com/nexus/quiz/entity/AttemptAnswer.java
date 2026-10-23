package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "attempt_answers",
        uniqueConstraints = @UniqueConstraint(name = "uq_attempt_answer",
                columnNames = {"attempt_id", "quiz_question_id"}),
        indexes = @Index(name = "idx_attempt_answers",
                columnList = "attempt_id, quiz_question_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aa_attempt"))
    private QuizAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aa_quiz_question"))
    private QuizQuestion quizQuestion;

    @Column(name = "selected_option_key", length = 10)
    private String selectedOptionKey;

    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "marks_awarded", precision = 8, scale = 2)
    private BigDecimal marksAwarded;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_status", nullable = false)
    @Builder.Default
    private GradingStatus gradingStatus = GradingStatus.PENDING;

    @Column(name = "faculty_feedback", columnDefinition = "TEXT")
    private String facultyFeedback;

    @Column(name = "marked_for_review", nullable = false)
    @Builder.Default
    private Boolean markedForReview = false;
}
