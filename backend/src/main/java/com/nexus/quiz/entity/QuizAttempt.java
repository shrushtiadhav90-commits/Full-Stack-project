package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "quiz_attempts",
        uniqueConstraints = @UniqueConstraint(name = "uq_attempt_student_quiz_number",
                columnNames = {"student_id", "quiz_id", "attempt_number"}),
        indexes = {
                @Index(name = "idx_attempts_student_quiz", columnList = "student_id, quiz_id"),
                @Index(name = "idx_attempts_status_deadline",
                        columnList = "status, effective_deadline")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attempt_quiz"))
    private Quiz quiz;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_attempt_student"))
    private Student student;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private Integer attemptNumber = 1;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant startedAt;

    @Column(name = "submitted_at", columnDefinition = "DATETIME(6)")
    private Instant submittedAt;

    @Column(name = "effective_deadline", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant effectiveDeadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "total_score", precision = 8, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "time_taken_seconds")
    private Integer timeTakenSeconds;

    @Column(name = "is_auto_submitted", nullable = false)
    @Builder.Default
    private Boolean isAutoSubmitted = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_order", columnDefinition = "JSON")
    private List<Long> questionOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "option_orders", columnDefinition = "JSON")
    private Map<String, List<String>> optionOrders;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;

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
