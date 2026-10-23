package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_classes",
        uniqueConstraints = @UniqueConstraint(name = "uq_quiz_class",
                columnNames = {"quiz_id", "class_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_qc_quiz"))
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_qc_class"))
    private SchoolClass schoolClass;
}
