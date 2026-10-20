package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;

import java.time.Instant;

@Entity
@Table(name = "parent_student",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_parent_student",
                columnNames = {"parent_user_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_parent_student_parent"))
    private User parent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_parent_student_student"))
    private Student student;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "UPPER(relation)")
    @Column(name = "relation", nullable = false, length = 20)
    private Relation relation;

    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAtUtc;

    @PrePersist
    void prePersist() {
        if (createdAtUtc == null) {
            createdAtUtc = Instant.now();
        }
    }

    public enum Relation {
        FATHER, MOTHER, GUARDIAN
    }
}
