package com.nexus.quiz.dto.admin;

import com.nexus.quiz.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 50) String username,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        @NotNull Role role,
        Long classId,
        @Size(max = 20) String rollNumber,
        @Size(max = 30) String admissionNumber) {
}
