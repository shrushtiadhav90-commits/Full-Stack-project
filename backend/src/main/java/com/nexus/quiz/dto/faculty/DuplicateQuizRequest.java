package com.nexus.quiz.dto.faculty;

import jakarta.validation.constraints.Size;

public record DuplicateQuizRequest(@Size(max = 200) String title) {
}
