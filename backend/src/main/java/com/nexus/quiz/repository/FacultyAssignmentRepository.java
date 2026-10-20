package com.nexus.quiz.repository;

import com.nexus.quiz.entity.FacultyAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface FacultyAssignmentRepository extends JpaRepository<FacultyAssignment, Long>,
        JpaSpecificationExecutor<FacultyAssignment> {

    List<FacultyAssignment> findByFacultyId(Long facultyId);

    boolean existsByFacultyIdAndSchoolClassIdAndSubjectId(
            Long facultyId, Long schoolClassId, Long subjectId);

    boolean existsBySubjectId(Long subjectId);

    Page<FacultyAssignment> findAllBy(Pageable pageable);
}
