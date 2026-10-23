package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "question_options",
        uniqueConstraints = @UniqueConstraint(name = "uq_question_option_key",
                columnNames = {"question_id", "option_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_qo_question"))
    private Question question;

    @Column(name = "option_key", nullable = false, length = 10)
    private String optionKey;

    @Column(name = "option_text", nullable = false, columnDefinition = "TEXT")
    private String optionText;

    @Column(name = "is_correct", nullable = false)
    @Builder.Default
    private Boolean isCorrect = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
