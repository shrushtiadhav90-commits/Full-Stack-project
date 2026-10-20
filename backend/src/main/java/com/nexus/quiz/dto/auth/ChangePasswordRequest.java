package com.nexus.quiz.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "New password must be at least 8 characters")
        @Pattern(regexp = "(?s).*[A-Z].*", message = "New password must contain an uppercase letter")
        @Pattern(regexp = "(?s).*[a-z].*", message = "New password must contain a lowercase letter")
        @Pattern(regexp = "(?s).*\\d.*", message = "New password must contain a number")
        String newPassword) {
}
