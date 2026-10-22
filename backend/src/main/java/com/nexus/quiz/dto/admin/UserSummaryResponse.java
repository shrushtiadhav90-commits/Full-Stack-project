package com.nexus.quiz.dto.admin;

import com.nexus.quiz.entity.Role;

public record UserSummaryResponse(
        Long id,
        String fullName,
        String username,
        String email,
        String phone,
        Role role,
        boolean isActive,
        boolean mustChangePassword,
        Long studentId,
        Long classId,
        String className,
        String section,
        String rollNumber,
        String admissionNumber) {
}
