package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditUserRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Size(max = 50) String username,
        @Email @Size(max = 150) String email,
        @Size(max = 20) String phone,
        Long classId,
        @Size(max = 20) String rollNumber,
        @Size(max = 30) String admissionNumber) {
}
