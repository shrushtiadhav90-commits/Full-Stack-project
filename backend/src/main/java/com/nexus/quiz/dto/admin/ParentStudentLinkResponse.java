package com.nexus.quiz.dto.admin;

import com.nexus.quiz.entity.ParentStudent;

public record ParentStudentLinkResponse(
        Long id,
        Long parentUserId,
        String parentName,
        Long studentId,
        String studentName,
        ParentStudent.Relation relation) {
}
