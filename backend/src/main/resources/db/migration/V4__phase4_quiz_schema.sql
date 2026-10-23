-- Bring the quiz tables from V1 in line with the Phase 4 data model.
-- V1-V3 are immutable; this migration runs against the existing empty quiz tables.

ALTER TABLE questions
    CHANGE COLUMN created_at_utc created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHANGE COLUMN updated_at_utc updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

ALTER TABLE quizzes
    CHANGE COLUMN start_time_utc start_time DATETIME(6) NOT NULL,
    CHANGE COLUMN end_time_utc end_time DATETIME(6) NOT NULL,
    CHANGE COLUMN created_at_utc created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHANGE COLUMN updated_at_utc updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    MODIFY COLUMN show_answers ENUM('NEVER','AFTER_SUBMIT','AFTER_END')
        NOT NULL DEFAULT 'AFTER_END',
    MODIFY COLUMN status ENUM('DRAFT','SCHEDULED','CLOSED') NOT NULL DEFAULT 'DRAFT';

ALTER TABLE quiz_question_snapshots
    CHANGE COLUMN type question_type ENUM('MCQ','TRUE_FALSE','SHORT_ANSWER') NOT NULL,
    CHANGE COLUMN created_at_utc created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

ALTER TABLE quiz_attempts
    CHANGE COLUMN started_at_utc started_at DATETIME(6) NOT NULL,
    CHANGE COLUMN submitted_at_utc submitted_at DATETIME(6) NULL,
    CHANGE COLUMN effective_deadline_utc effective_deadline DATETIME(6) NOT NULL,
    CHANGE COLUMN created_at_utc created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHANGE COLUMN updated_at_utc updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    ADD CONSTRAINT uq_attempt_student_quiz_number
        UNIQUE (student_id, quiz_id, attempt_number);

DROP INDEX idx_attempts_deadline_status ON quiz_attempts;
CREATE INDEX idx_attempts_status_deadline ON quiz_attempts (status, effective_deadline);
