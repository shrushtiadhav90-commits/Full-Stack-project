package com.nexus.quiz.repository;

import com.nexus.quiz.entity.FacultyAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacultyAssignmentRepository extends JpaRepository<FacultyAssignment, Long> {

    List<FacultyAssignment> findByFacultyId(Long facultyId);

    boolean existsByFacultyIdAndSchoolClassIdAndSubjectId(
            Long facultyId, Long schoolClassId, Long subjectId);

    boolean existsBySubjectId(Long subjectId);

    Page<FacultyAssignment> findAllBy(Pageable pageable);
}
