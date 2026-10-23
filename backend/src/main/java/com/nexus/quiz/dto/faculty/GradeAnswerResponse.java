package com.nexus.quiz.dto.faculty;

import com.nexus.quiz.entity.GradingStatus;

import java.math.BigDecimal;

public record GradeAnswerResponse(
        Long answerId,
        BigDecimal marksAwarded,
        GradingStatus gradingStatus,
        boolean attemptFullyGraded) {
}
