package com.nexus.quiz.dto.admin;

import java.util.List;

public record BulkUserResponse(
        int successCount,
        int failureCount,
        List<RowFailure> failures,
        List<CreatedUserResponse> createdUsers) {

    public record RowFailure(int rowNumber, String reason) {
    }
}
