-- =============================================================================
-- V1__init_schema.sql
-- Nexus Quiz Portal – initial schema
-- All DATETIME columns use DATETIME(6) (microsecond precision), stored in UTC.
-- All comparisons must use UTC_TIMESTAMP() or pass UTC Instants from Java.
--
-- NOTE ON DEFAULTS:
--   MySQL 8.0 supports CURRENT_TIMESTAMP(6) as a column default/on-update
--   expression for DATETIME(6) columns.  The parenthesised functional-default
--   syntax DEFAULT (UTC_TIMESTAMP(6)) is NOT accepted by MySQL 8.0 because
--   UTC_TIMESTAMP with a precision argument inside DEFAULT (...) is rejected.
--   Use DEFAULT CURRENT_TIMESTAMP(6) / ON UPDATE CURRENT_TIMESTAMP(6) instead.
--   These store the current UTC wall-clock time because the JDBC connection
--   uses serverTimezone=UTC and the JVM is started with -Duser.timezone=UTC.
-- =============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- -----------------------------------------------------------------------------
-- users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                   BIGINT          NOT NULL AUTO_INCREMENT,
    full_name            VARCHAR(100)    NOT NULL,
    username             VARCHAR(50)     NOT NULL,
    email                VARCHAR(150)    NULL,
    phone                VARCHAR(20)     NULL,
    password_hash        VARCHAR(255)    NOT NULL,
    role                 ENUM('STUDENT','PARENT','FACULTY','ADMIN') NOT NULL,
    is_active            TINYINT(1)      NOT NULL DEFAULT 1,
    must_change_password TINYINT(1)      NOT NULL DEFAULT 1,
    failed_login_count   INT             NOT NULL DEFAULT 0,
    locked_until_utc     DATETIME(6)     NULL,
    profile_photo_url    VARCHAR(512)    NULL,
    created_at_utc       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc       DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username)
    -- email uniqueness enforced in application logic for nullable column
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- academic_years
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_years (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(20)  NOT NULL,
    start_date     DATE         NOT NULL,
    end_date       DATE         NOT NULL,
    is_current     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_academic_years_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- classes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS classes (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(50)  NOT NULL,
    section          VARCHAR(10)  NOT NULL,
    academic_year_id BIGINT       NOT NULL,
    created_at_utc   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_classes_name_section_year (name, section, academic_year_id),
    CONSTRAINT fk_classes_academic_year
        FOREIGN KEY (academic_year_id) REFERENCES academic_years (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- subjects
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subjects (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL,
    code           VARCHAR(20)  NOT NULL,
    created_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- students
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    class_id         BIGINT       NOT NULL,
    roll_number      VARCHAR(20)  NULL,
    admission_number VARCHAR(30)  NULL,
    created_at_utc   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_students_user_id (user_id),
    CONSTRAINT fk_students_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_students_class
        FOREIGN KEY (class_id) REFERENCES classes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index: class roster queries
CREATE INDEX idx_students_class_id ON students (class_id);

-- -----------------------------------------------------------------------------
-- parent_student
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parent_student (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    parent_user_id  BIGINT       NOT NULL,
    student_id      BIGINT       NOT NULL,
    relation        ENUM('Father','Mother','Guardian') NOT NULL,
    created_at_utc  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_parent_student (parent_user_id, student_id),
    CONSTRAINT fk_parent_student_parent
        FOREIGN KEY (parent_user_id) REFERENCES users (id),
    CONSTRAINT fk_parent_student_student
        FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- faculty_assignments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS faculty_assignments (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    faculty_user_id  BIGINT      NOT NULL,
    class_id         BIGINT      NOT NULL,
    subject_id       BIGINT      NOT NULL,
    created_at_utc   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_faculty_assignment (faculty_user_id, class_id, subject_id),
    CONSTRAINT fk_fa_faculty
        FOREIGN KEY (faculty_user_id) REFERENCES users (id),
    CONSTRAINT fk_fa_class
        FOREIGN KEY (class_id) REFERENCES classes (id),
    CONSTRAINT fk_fa_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- questions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS questions (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    created_by     BIGINT       NOT NULL,
    subject_id     BIGINT       NOT NULL,
    class_level    VARCHAR(20)  NOT NULL,
    topic          VARCHAR(100) NULL,
    type           ENUM('MCQ','TRUE_FALSE','SHORT_ANSWER') NOT NULL,
    text           TEXT         NOT NULL,
    image_url      VARCHAR(512) NULL,
    difficulty     ENUM('EASY','MEDIUM','HARD') NOT NULL DEFAULT 'MEDIUM',
    marks          INT          NOT NULL DEFAULT 1,
    explanation    TEXT         NULL,
    model_answer   TEXT         NULL,
    is_active      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_questions_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_questions_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- question_options
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS question_options (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    question_id  BIGINT       NOT NULL,
    option_key   VARCHAR(10)  NOT NULL,   -- stable string key, e.g. "opt_a"
    option_text  TEXT         NOT NULL,
    is_correct   TINYINT(1)   NOT NULL DEFAULT 0,
    sort_order   INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uq_question_option_key (question_id, option_key),
    CONSTRAINT fk_qo_question
        FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- quizzes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quizzes (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    title               VARCHAR(200)    NOT NULL,
    description         TEXT            NULL,
    subject_id          BIGINT          NOT NULL,
    created_by          BIGINT          NOT NULL,
    duration_minutes    INT             NOT NULL,
    start_time_utc      DATETIME(6)     NOT NULL,
    end_time_utc        DATETIME(6)     NOT NULL,
    max_attempts        INT             NOT NULL DEFAULT 1,
    shuffle_questions   TINYINT(1)      NOT NULL DEFAULT 0,
    shuffle_options     TINYINT(1)      NOT NULL DEFAULT 0,
    show_answers        ENUM('NEVER','AFTER_SUBMIT','AFTER_END') NOT NULL DEFAULT 'NEVER',
    pass_percentage     DECIMAL(5,2)    NOT NULL DEFAULT 40.00,
    total_marks         INT             NOT NULL DEFAULT 0,
    status              ENUM('DRAFT','SCHEDULED','LIVE','CLOSED') NOT NULL DEFAULT 'DRAFT',
    reminder_24h_sent   TINYINT(1)      NOT NULL DEFAULT 0,
    reminder_1h_sent    TINYINT(1)      NOT NULL DEFAULT 0,
    created_at_utc      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_quizzes_subject
        FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_quizzes_created_by
        FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for scheduler and live-quiz lookup
CREATE INDEX idx_quizzes_start_time   ON quizzes (start_time_utc);
CREATE INDEX idx_quizzes_status_start ON quizzes (status, start_time_utc);

-- -----------------------------------------------------------------------------
-- quiz_classes  (many-to-many: quiz → classes)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_classes (
    id       BIGINT NOT NULL AUTO_INCREMENT,
    quiz_id  BIGINT NOT NULL,
    class_id BIGINT NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_quiz_class (quiz_id, class_id),
    CONSTRAINT fk_qc_quiz
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_qc_class
        FOREIGN KEY (class_id) REFERENCES classes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- quiz_questions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_questions (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    quiz_id     BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sort_order  INT    NOT NULL DEFAULT 0,
    marks       INT    NOT NULL DEFAULT 1,

    PRIMARY KEY (id),
    UNIQUE KEY uq_quiz_question (quiz_id, question_id),
    CONSTRAINT fk_qq_quiz
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_qq_question
        FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- quiz_question_snapshots  (write-once at publish time)
-- Protects scoring from later question edits.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_question_snapshots (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    quiz_id             BIGINT          NOT NULL,
    question_id         BIGINT          NOT NULL,
    snapshot_text       TEXT            NOT NULL,
    snapshot_options    JSON            NULL,        -- [{key, text}, ...]
    correct_option_key  VARCHAR(10)     NULL,        -- null for SHORT_ANSWER
    marks               INT             NOT NULL DEFAULT 1,
    type                ENUM('MCQ','TRUE_FALSE','SHORT_ANSWER') NOT NULL,
    model_answer        TEXT            NULL,
    explanation         TEXT            NULL,
    image_url           VARCHAR(512)    NULL,
    created_at_utc      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_snapshot (quiz_id, question_id),
    CONSTRAINT fk_snap_quiz
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE,
    CONSTRAINT fk_snap_question
        FOREIGN KEY (question_id) REFERENCES questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- quiz_attempts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quiz_attempts (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    quiz_id                 BIGINT          NOT NULL,
    student_id              BIGINT          NOT NULL,
    attempt_number          INT             NOT NULL DEFAULT 1,
    started_at_utc          DATETIME(6)     NOT NULL,
    submitted_at_utc        DATETIME(6)     NULL,
    effective_deadline_utc  DATETIME(6)     NOT NULL,
    status                  ENUM('IN_PROGRESS','SUBMITTED','GRADED') NOT NULL DEFAULT 'IN_PROGRESS',
    total_score             DECIMAL(8,2)    NULL,
    percentage              DECIMAL(5,2)    NULL,
    time_taken_seconds      INT             NULL,
    is_auto_submitted       TINYINT(1)      NOT NULL DEFAULT 0,
    question_order          JSON            NULL,   -- array of quiz_question_id
    option_orders           JSON            NULL,   -- map quiz_question_id → [option_key,...]
    created_at_utc          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_attempt_quiz
        FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT fk_attempt_student
        FOREIGN KEY (student_id) REFERENCES students (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for scheduler and deadline queries
CREATE INDEX idx_attempts_student_quiz    ON quiz_attempts (student_id, quiz_id);
CREATE INDEX idx_attempts_deadline_status ON quiz_attempts (effective_deadline_utc, status);

-- -----------------------------------------------------------------------------
-- attempt_answers
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attempt_answers (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    attempt_id          BIGINT          NOT NULL,
    quiz_question_id    BIGINT          NOT NULL,   -- FK to quiz_questions
    selected_option_key VARCHAR(10)     NULL,       -- matches snapshot.correct_option_key
    text_answer         TEXT            NULL,
    is_correct          TINYINT(1)      NULL,
    marks_awarded       DECIMAL(8,2)    NULL,
    grading_status      ENUM('PENDING','AUTO_GRADED','GRADED') NOT NULL DEFAULT 'PENDING',
    faculty_feedback    TEXT            NULL,
    marked_for_review   TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE KEY uq_attempt_answer (attempt_id, quiz_question_id),
    CONSTRAINT fk_aa_attempt
        FOREIGN KEY (attempt_id) REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_aa_quiz_question
        FOREIGN KEY (quiz_question_id) REFERENCES quiz_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for answer lookup
CREATE INDEX idx_attempt_answers ON attempt_answers (attempt_id, quiz_question_id);

-- -----------------------------------------------------------------------------
-- badges
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS badges (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)     NOT NULL,
    name           VARCHAR(100)    NOT NULL,
    description    TEXT            NULL,
    icon           VARCHAR(100)    NOT NULL,
    type           ENUM('SCORE','SPECIAL') NOT NULL,
    min_percentage DECIMAL(5,2)    NULL,
    is_active      TINYINT(1)      NOT NULL DEFAULT 1,
    created_at_utc DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at_utc DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_badges_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- student_badges
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_badges (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    student_id       BIGINT      NOT NULL,
    badge_id         BIGINT      NOT NULL,
    quiz_attempt_id  BIGINT      NULL,
    quiz_id          BIGINT      NULL,   -- enables unique constraint per quiz
    awarded_at_utc   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    -- quiz_id null handling: unique enforced in application logic for null quiz_id badges
    UNIQUE KEY uq_student_badge_quiz (student_id, badge_id, quiz_id),
    CONSTRAINT fk_sb_student
        FOREIGN KEY (student_id) REFERENCES students (id),
    CONSTRAINT fk_sb_badge
        FOREIGN KEY (badge_id) REFERENCES badges (id),
    CONSTRAINT fk_sb_attempt
        FOREIGN KEY (quiz_attempt_id) REFERENCES quiz_attempts (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for badge shelf queries
CREATE INDEX idx_student_badges ON student_badges (student_id, badge_id);

-- -----------------------------------------------------------------------------
-- notifications
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id             BIGINT          NOT NULL AUTO_INCREMENT,
    user_id        BIGINT          NOT NULL,
    title          VARCHAR(200)    NOT NULL,
    message        TEXT            NOT NULL,
    type           VARCHAR(50)     NOT NULL,
    link           VARCHAR(512)    NULL,
    is_read        TINYINT(1)      NOT NULL DEFAULT 0,
    created_at_utc DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for bell unread count
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);

-- -----------------------------------------------------------------------------
-- refresh_tokens
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    token_hash     VARCHAR(64)  NOT NULL,
    expires_at_utc DATETIME(6)  NOT NULL,
    revoked        TINYINT(1)   NOT NULL DEFAULT 0,
    created_at_utc DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_token_hash (token_hash),
    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for refresh lookup
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens (token_hash);

-- -----------------------------------------------------------------------------
-- school_settings  (single-row table, id=1)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS school_settings (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    school_name                 VARCHAR(200) NOT NULL DEFAULT 'Nexus Ranaji English Medium School',
    logo_url                    VARCHAR(512) NULL,
    primary_color               VARCHAR(20)  NOT NULL DEFAULT '#2563EB',
    secondary_color             VARCHAR(20)  NOT NULL DEFAULT '#F59E0B',
    leaderboard_enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    email_notifications_enabled TINYINT(1)   NOT NULL DEFAULT 0,
    updated_at_utc              DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed the single settings row
INSERT IGNORE INTO school_settings (id, school_name) VALUES (1, 'Nexus Ranaji English Medium School');

-- -----------------------------------------------------------------------------
-- audit_log
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    admin_user_id       BIGINT       NOT NULL,
    action_type         VARCHAR(50)  NOT NULL,
    target_entity_type  VARCHAR(50)  NOT NULL,
    target_entity_id    BIGINT       NULL,
    old_value           JSON         NULL,
    new_value           JSON         NULL,
    created_at_utc      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Index for audit page queries
CREATE INDEX idx_audit_log_admin_created ON audit_log (admin_user_id, created_at_utc);
