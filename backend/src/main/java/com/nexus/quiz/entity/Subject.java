package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "subjects",
        uniqueConstraints = @UniqueConstraint(name = "uq_subjects_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Unique short code, e.g. "ENG", "MATH". */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

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
