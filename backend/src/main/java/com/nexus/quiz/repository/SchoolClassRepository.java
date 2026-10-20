package com.nexus.quiz.repository;

import com.nexus.quiz.entity.SchoolClass;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    boolean existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(
            String name, String section, Long academicYearId);

    boolean existsByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearIdAndIdNot(
            String name, String section, Long academicYearId, Long id);

    List<SchoolClass> findByNameIgnoreCaseAndSectionIgnoreCase(String name, String section);

    List<SchoolClass> findByNameIgnoreCaseAndSectionIgnoreCaseAndAcademicYearId(
            String name, String section, Long academicYearId);

    Page<SchoolClass> findAllBy(Pageable pageable);
}
