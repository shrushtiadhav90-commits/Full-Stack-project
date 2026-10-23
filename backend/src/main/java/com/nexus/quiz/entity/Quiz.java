package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_start_time", columnList = "start_time"),
        @Index(name = "idx_quizzes_status_start", columnList = "status, start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz {

    public enum EffectiveStatus {
        DRAFT,
        SCHEDULED,
        LIVE,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quizzes_subject"))
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_quizzes_created_by"))
    private User createdBy;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "start_time", columnDefinition = "DATETIME(6)")
    private Instant startTime;

    @Column(name = "end_time", columnDefinition = "DATETIME(6)")
    private Instant endTime;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 1;

    @Column(name = "shuffle_questions", nullable = false)
    @Builder.Default
    private Boolean shuffleQuestions = false;

    @Column(name = "shuffle_options", nullable = false)
    @Builder.Default
    private Boolean shuffleOptions = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "show_answers", nullable = false)
    @Builder.Default
    private ShowAnswers showAnswers = ShowAnswers.AFTER_END;

    @Column(name = "pass_percentage", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal passPercentage = new BigDecimal("40.00");

    @Column(name = "total_marks", nullable = false)
    @Builder.Default
    private Integer totalMarks = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private QuizStatus status = QuizStatus.DRAFT;

    @Column(name = "reminder_24h_sent", nullable = false)
    @Builder.Default
    private Boolean reminder24hSent = false;

    @Column(name = "reminder_1h_sent", nullable = false)
    @Builder.Default
    private Boolean reminder1hSent = false;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

    public EffectiveStatus effectiveStatusAt(Instant now) {
        return QuizStatusHelper.derivedStatusAt(this, Objects.requireNonNull(now, "now must not be null"));
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
