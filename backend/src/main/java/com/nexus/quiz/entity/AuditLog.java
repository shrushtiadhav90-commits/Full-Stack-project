package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.regex.Pattern;

@Entity
@Table(name = "audit_log",
        indexes = @Index(name = "idx_audit_log_admin_created",
                columnList = "admin_user_id, created_at_utc"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    private static final Pattern PASSWORD_FIELD =
            Pattern.compile("\"[^\"\\\\]*password[^\"\\\\]*\"\\s*:", Pattern.CASE_INSENSITIVE);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "target_entity_type", nullable = false, length = 50)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private Long targetEntityId;

    @Column(name = "old_value", columnDefinition = "JSON")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSON")
    private String newValue;

    @Column(name = "created_at_utc", nullable = false, updatable = false,
            columnDefinition = "DATETIME(6)")
    private Instant createdAtUtc;

    @PrePersist
    void prePersist() {
        rejectPasswordFields(oldValue);
        rejectPasswordFields(newValue);
        if (createdAtUtc == null) {
            createdAtUtc = Instant.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        rejectPasswordFields(oldValue);
        rejectPasswordFields(newValue);
    }

    private void rejectPasswordFields(String value) {
        if (value != null && PASSWORD_FIELD.matcher(value).find()) {
            throw new IllegalArgumentException("Audit values must not contain password fields");
        }
    }
}
