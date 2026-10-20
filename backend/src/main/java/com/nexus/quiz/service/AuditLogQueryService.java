package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.AuditLogResponse;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.entity.AuditLog;
import com.nexus.quiz.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class AuditLogQueryService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuditLogRepository auditLogRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(
            int page, int size, String actionType, Instant fromUtc, Instant toUtc) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be from 1 to 100");
        }
        if (fromUtc != null && toUtc != null && fromUtc.isAfter(toUtc)) {
            throw new IllegalArgumentException("The start date must not be after the end date");
        }
        Specification<AuditLog> specification = Specification.where(null);
        if (actionType != null && !actionType.isBlank()) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("actionType"), actionType.trim()));
        }
        if (fromUtc != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("createdAtUtc"), fromUtc));
        }
        if (toUtc != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("createdAtUtc"), toUtc));
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAtUtc"));
        return PageResponse.from(auditLogRepository.findAll(specification, pageable)
                .map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getAdminUserId(), log.getActionType(),
                log.getTargetEntityType(), log.getTargetEntityId(), log.getOldValue(),
                log.getNewValue(), IST_FORMAT.format(log.getCreatedAtUtc().atZone(IST)));
    }
}
