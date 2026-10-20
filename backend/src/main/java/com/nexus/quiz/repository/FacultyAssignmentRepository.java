package com.nexus.quiz.repository;

import com.nexus.quiz.entity.FacultyAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacultyAssignmentRepository extends JpaRepository<FacultyAssignment, Long> {

    List<FacultyAssignment> findByFacultyId(Long facultyId);
}
