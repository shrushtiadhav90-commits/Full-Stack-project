package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "academic_years",
        uniqueConstraints = @UniqueConstraint(name = "uq_academic_years_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E.g. "2025-26" */
    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Exactly one row should have isCurrent=true at any time. */
    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private Boolean isCurrent = false;

    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAtUtc;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAtUtc = now;
        this.updatedAtUtc = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAtUtc = Instant.now();
    }
}
