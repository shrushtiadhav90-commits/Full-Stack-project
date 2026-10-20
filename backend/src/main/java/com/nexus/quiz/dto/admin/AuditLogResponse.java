package com.nexus.quiz.dto.admin;

public record AuditLogResponse(
        Long id,
        Long adminUserId,
        String actionType,
        String targetEntityType,
        Long targetEntityId,
        String oldValue,
        String newValue,
        String createdAt) {
}
