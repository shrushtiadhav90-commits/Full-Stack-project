package com.nexus.quiz.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String code) {
}
