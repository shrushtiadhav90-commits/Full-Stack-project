package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "classes", uniqueConstraints = @UniqueConstraint(
        name = "uq_classes_name_section_year",
        columnNames = {"name", "section", "academic_year_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E.g. "Grade 8" */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** E.g. "A", "B", "Science" */
    @Column(name = "section", nullable = false, length = 10)
    private String section;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;

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
