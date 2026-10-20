package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "faculty_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_faculty_assignment",
                columnNames = {"faculty_user_id", "class_id", "subject_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fa_faculty"))
    private User faculty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fa_class"))
    private SchoolClass schoolClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subject_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_fa_subject"))
    private Subject subject;

    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAtUtc;

    @PrePersist
    void prePersist() {
        if (createdAtUtc == null) {
            createdAtUtc = Instant.now();
        }
    }
}
