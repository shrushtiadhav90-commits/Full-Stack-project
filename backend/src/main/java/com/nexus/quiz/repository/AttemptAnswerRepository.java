package com.nexus.quiz.repository;

import com.nexus.quiz.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    java.util.List<AttemptAnswer> findByAttemptIdOrderByIdAsc(Long attemptId);

    boolean existsByAttemptIdAndGradingStatus(
            Long attemptId, com.nexus.quiz.entity.GradingStatus gradingStatus);

    @Query("""
            select count(answer) > 0
            from AttemptAnswer answer
            join answer.quizQuestion quizQuestion
            join QuizQuestionSnapshot snapshot
              on snapshot.quiz.id = answer.attempt.quiz.id
                 and snapshot.question.id = quizQuestion.question.id
            where answer.attempt.id = :attemptId
              and answer.gradingStatus = com.nexus.quiz.entity.GradingStatus.PENDING
              and snapshot.questionType = com.nexus.quiz.entity.QuestionType.SHORT_ANSWER
            """)
    boolean existsPendingShortAnswer(@Param("attemptId") Long attemptId);

    @Query("""
            select new com.nexus.quiz.dto.faculty.GradingQueueItemResponse(
                answer.id, quiz.id, quiz.title, student.user.fullName,
                schoolClass.name, schoolClass.section, snapshot.snapshotText,
                snapshot.modelAnswer, answer.textAnswer, snapshot.marks,
                attempt.id)
            from AttemptAnswer answer
            join answer.attempt attempt
            join attempt.quiz quiz
            join attempt.student student
            join student.schoolClass schoolClass
            join answer.quizQuestion quizQuestion
            join quizQuestion.question question
            join QuizQuestionSnapshot snapshot
              on snapshot.quiz.id = quiz.id and snapshot.question.id = question.id
            where attempt.status = com.nexus.quiz.entity.AttemptStatus.SUBMITTED
              and answer.gradingStatus = com.nexus.quiz.entity.GradingStatus.PENDING
              and snapshot.questionType = com.nexus.quiz.entity.QuestionType.SHORT_ANSWER
              and quiz.createdBy.id = :facultyId
              and exists (
                  select assignment.id from FacultyAssignment assignment
                  where assignment.faculty.id = :facultyId
                    and assignment.schoolClass.id = schoolClass.id
                    and assignment.subject.id = quiz.subject.id
              )
              and (:quizId is null or quiz.id = :quizId)
              and (:classId is null or schoolClass.id = :classId)
            order by attempt.submittedAt asc, answer.id asc
            """)
    Page<com.nexus.quiz.dto.faculty.GradingQueueItemResponse> findPendingShortAnswers(
            @Param("facultyId") Long facultyId,
            @Param("quizId") Long quizId,
            @Param("classId") Long classId,
            Pageable pageable);

    @Query("""
            select count(answer)
            from AttemptAnswer answer
            join answer.attempt attempt
            join attempt.quiz quiz
            join attempt.student student
            join student.schoolClass schoolClass
            join answer.quizQuestion quizQuestion
            join quizQuestion.question question
            join QuizQuestionSnapshot snapshot
              on snapshot.quiz.id = quiz.id and snapshot.question.id = question.id
            where attempt.status = com.nexus.quiz.entity.AttemptStatus.SUBMITTED
              and answer.gradingStatus = com.nexus.quiz.entity.GradingStatus.PENDING
              and snapshot.questionType = com.nexus.quiz.entity.QuestionType.SHORT_ANSWER
              and quiz.createdBy.id = :facultyId
              and exists (
                  select assignment.id from FacultyAssignment assignment
                  where assignment.faculty.id = :facultyId
                    and assignment.schoolClass.id = schoolClass.id
                    and assignment.subject.id = quiz.subject.id
              )
            """)
    long countPendingShortAnswers(@Param("facultyId") Long facultyId);
}
