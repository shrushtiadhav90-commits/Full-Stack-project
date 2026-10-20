package com.nexus.quiz.repository;

import com.nexus.quiz.entity.SchoolSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolSettingsRepository extends JpaRepository<SchoolSettings, Long> {
}
