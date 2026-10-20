package com.nexus.quiz.repository;

import com.nexus.quiz.entity.ParentStudent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParentStudentRepository extends JpaRepository<ParentStudent, Long> {

    List<ParentStudent> findByParentId(Long parentId);

    List<ParentStudent> findByStudentId(Long studentId);

    long countByStudentId(Long studentId);

    boolean existsByParentIdAndStudentId(Long parentId, Long studentId);

    Page<ParentStudent> findAllBy(Pageable pageable);
}
