package com.nexus.quiz.repository;

import com.nexus.quiz.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByUserId(Long userId);

    boolean existsBySchoolClassId(Long schoolClassId);

    long countBySchoolClassId(Long schoolClassId);

    List<Student> findBySchoolClassIdInOrderByUserFullNameAsc(List<Long> schoolClassIds);
}
