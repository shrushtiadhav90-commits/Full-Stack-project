package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_questions",
        uniqueConstraints = @UniqueConstraint(name = "uq_quiz_question",
                columnNames = {"quiz_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_qq_quiz"))
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_qq_question"))
    private Question question;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "marks", nullable = false)
    @Builder.Default
    private Integer marks = 1;
}
