package com.nexus.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.quiz.entity.AuditLog;
import com.nexus.quiz.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AuditLog record(
            Long adminUserId,
            String actionType,
            String targetEntityType,
            Long targetEntityId,
            Map<String, ?> oldValue,
            Map<String, ?> newValue) {
        AuditLog auditLog = AuditLog.builder()
                .adminUserId(adminUserId)
                .actionType(actionType)
                .targetEntityType(targetEntityType)
                .targetEntityId(targetEntityId)
                .oldValue(toJson(oldValue))
                .newValue(toJson(newValue))
                .build();
        return auditLogRepository.save(auditLog);
    }

    private String toJson(Map<String, ?> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        rejectSensitiveKeys(value);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Audit values could not be serialized", exception);
        }
    }

    private void rejectSensitiveKeys(Map<String, ?> values) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (containsSensitiveWord(entry.getKey())) {
                throw new IllegalArgumentException("Audit values must not contain password fields");
            }
            rejectSensitiveValue(entry.getValue());
        }
    }

    private void rejectSensitiveValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            for (Map.Entry<?, ?> entry : nested.entrySet()) {
                if (entry.getKey() instanceof String key && containsSensitiveWord(key)) {
                    throw new IllegalArgumentException("Audit values must not contain password fields");
                }
                rejectSensitiveValue(entry.getValue());
            }
        } else if (value instanceof Iterable<?> values) {
            values.forEach(this::rejectSensitiveValue);
        }
    }

    private boolean containsSensitiveWord(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password") || normalized.contains("passwd");
    }
}
