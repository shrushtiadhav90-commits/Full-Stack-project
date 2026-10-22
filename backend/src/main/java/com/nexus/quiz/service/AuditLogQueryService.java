package com.nexus.quiz.service;

import com.nexus.quiz.dto.admin.AuditLogResponse;
import com.nexus.quiz.dto.admin.PageResponse;
import com.nexus.quiz.entity.AuditLog;
import com.nexus.quiz.entity.User;
import com.nexus.quiz.repository.AuditLogRepository;
import com.nexus.quiz.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AuditLogQueryService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter IST_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogQueryService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
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
        var auditPage = auditLogRepository.findAll(specification, pageable);
        var adminIds = auditPage.getContent().stream()
                .map(AuditLog::getAdminUserId)
                .collect(Collectors.toSet());
        Map<Long, String> adminNames = new HashMap<>();
        for (User user : userRepository.findAllById(adminIds)) {
            adminNames.put(user.getId(), user.getFullName());
        }
        return PageResponse.from(auditPage.map(log ->
                toResponse(log, adminNames.getOrDefault(log.getAdminUserId(), "Unknown admin"))));
    }

    private AuditLogResponse toResponse(AuditLog log, String adminName) {
        return new AuditLogResponse(log.getId(), log.getAdminUserId(), adminName, log.getActionType(),
                log.getTargetEntityType(), log.getTargetEntityId(), log.getOldValue(),
                log.getNewValue(), IST_FORMAT.format(log.getCreatedAtUtc().atZone(IST)));
    }
}
