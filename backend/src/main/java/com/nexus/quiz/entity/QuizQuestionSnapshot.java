package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Immutable
@Table(name = "quiz_question_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uq_snapshot",
                columnNames = {"quiz_id", "question_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_snap_quiz"))
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_snap_question"))
    private Question question;

    @Column(name = "question_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuestionType questionType;

    @Column(name = "snapshot_text", nullable = false, columnDefinition = "TEXT")
    private String snapshotText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_options", columnDefinition = "JSON")
    private List<Map<String, String>> snapshotOptions;

    @Column(name = "correct_option_key", length = 10)
    private String correctOptionKey;

    @Column(name = "marks", nullable = false)
    @Builder.Default
    private Integer marks = 1;

    @Column(name = "model_answer", columnDefinition = "TEXT")
    private String modelAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
