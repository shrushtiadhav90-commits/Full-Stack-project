package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "classes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Class {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** E.g. "Grade 8" */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** E.g. "A", "B", "Science" */
    @Column(name = "section", length = 50)
    private String section;

    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
}
