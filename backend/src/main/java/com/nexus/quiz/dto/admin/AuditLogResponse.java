package com.nexus.quiz.dto.admin;

public record AuditLogResponse(
        Long id,
        Long adminUserId,
        String adminName,
        String actionType,
        String targetEntityType,
        Long targetEntityId,
        String oldValue,
        String newValue,
        String createdAt) {
}
