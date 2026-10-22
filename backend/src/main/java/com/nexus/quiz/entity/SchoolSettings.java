package com.nexus.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "school_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchoolSettings {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Column(name = "school_name", nullable = false, length = 200)
    @Builder.Default
    private String schoolName = "Nexus Ranaji English Medium School";

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(name = "primary_color", nullable = false, length = 20)
    @Builder.Default
    private String primaryColor = "#2563EB";

    @Column(name = "secondary_color", nullable = false, length = 20)
    @Builder.Default
    private String secondaryColor = "#F59E0B";

    @Column(name = "leaderboard_enabled", nullable = false)
    @Builder.Default
    private Boolean leaderboardEnabled = false;

    @Column(name = "email_notifications_enabled", nullable = false)
    @Builder.Default
    private Boolean emailNotificationsEnabled = false;

    @Column(name = "updated_at_utc", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAtUtc;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        id = 1L;
        updatedAtUtc = Instant.now();
    }
}
