package com.nexus.quiz.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class QuestionUsageRepository {

    private final JdbcTemplate jdbcTemplate;

    public QuestionUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsBySubjectId(Long subjectId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from questions where subject_id = ?",
                Integer.class,
                subjectId);
        return count != null && count > 0;
    }
}
