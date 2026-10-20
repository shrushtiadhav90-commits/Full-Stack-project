package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "students", uniqueConstraints =
        @UniqueConstraint(name = "uq_students_user_id", columnNames = "user_id"),
        indexes = @Index(name = "idx_students_class_id", columnList = "class_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_students_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_students_class"))
    private SchoolClass schoolClass;

    @Column(name = "roll_number", length = 20)
    private String rollNumber;

    @Column(name = "admission_number", length = 30)
    private String admissionNumber;

    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAtUtc;

    @Column(name = "updated_at_utc", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAtUtc;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAtUtc = now;
        updatedAtUtc = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAtUtc = Instant.now();
    }
}
