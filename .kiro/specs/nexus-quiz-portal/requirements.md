# Requirements Document

## Introduction

The Nexus Quiz Portal is a full-stack web application built for Nexus Ranaji English Medium School. It replaces manual quiz and result management with a structured digital platform where faculty create and schedule quizzes, students attempt them inside enforced time windows, and students, parents, faculty, and the principal (Admin) can track performance through role-specific dashboards.

The portal addresses three core problems: quizzes and results are currently managed on paper with no central record; parents have no visibility into their child's academic performance between term reports; and the principal has no single view of student progress or faculty activity across the school.

The system supports four user roles — Student, Parent, Faculty, and Admin — each with a dedicated dashboard, navigation layout, and strictly enforced data access boundary. Branding (school name, logo, colors) is configurable from a single theme file and an Admin settings page, so the school can apply its final brand assets without a code rebuild. All dates and times are stored in UTC and displayed in Asia/Kolkata (IST, UTC+5:30).

---

## Glossary

- **System**: The Nexus Quiz Portal web application as a whole.
- **Auth_Service**: The backend component responsible for authentication, token issuance, and session management.
- **User_Manager**: The backend component responsible for user account creation, deactivation, and password management.
- **Question_Bank**: The backend component and associated UI that stores and manages reusable quiz questions.
- **Quiz_Engine**: The backend component responsible for quiz creation, scheduling, status transitions, and publish validation.
- **Attempt_Engine**: The backend component that manages quiz attempts, enforces server-side timers, persists answers, and performs auto-submit.
- **Grading_Service**: The backend component responsible for faculty grading of short-answer responses and final score computation.
- **Badge_Service**: The backend component that evaluates and awards badges after an attempt reaches GRADED status.
- **Notification_Service**: The backend component that creates in-app notifications and (optionally) sends email notifications.
- **Analytics_Service**: The backend component that aggregates performance data for dashboards and reports.
- **Report_Service**: The backend component that generates CSV and PDF exports.
- **School_Settings_Service**: The backend component that manages school branding and configuration.
- **Admin**: The principal user role with full system access; manages users, academic structure, and system settings.
- **Faculty**: A teacher user role that creates questions, manages quizzes, grades short answers, and views analytics for assigned classes and subjects.
- **Student**: A learner user role that attempts quizzes and views own results, history, badges, and leaderboard.
- **Parent**: A guardian user role that views performance data for linked children only.
- **Academic_Year**: A named school year (e.g., "2026-27") with a defined start and end date.
- **Class**: A named grouping of students (e.g., "Class 6 - A") belonging to an Academic_Year.
- **Subject**: A named curriculum area (e.g., "Mathematics") identified by a unique code.
- **Faculty_Assignment**: A record linking a Faculty member to a specific Class and Subject combination.
- **Question**: A single assessment item of type MCQ, True/False, or Short_Answer stored in the Question_Bank.
- **Question_Snapshot**: An immutable copy of a question's text, options, correct answer, and marks value captured at the moment the first attempt on a quiz starts; used for all scoring and display of that quiz's attempts.
- **Quiz**: A timed, scheduled assessment composed of questions from the Question_Bank, targeting one or more Class sections.
- **Quiz_Status**: The lifecycle state of a quiz: DRAFT → SCHEDULED → LIVE → CLOSED. LIVE and CLOSED are derived from start_time and end_time relative to the current UTC time; DRAFT and SCHEDULED are explicit states set by the faculty.
- **Attempt**: A single student's instance of taking a quiz, with statuses IN_PROGRESS, SUBMITTED, or GRADED.
- **Effective_Deadline**: The earlier of (attempt start_time + quiz duration_minutes) and the quiz end_time, computed server-side in UTC.
- **Auto_Save**: The background mechanism that persists a student's changed answer to the server without interrupting navigation.
- **Grading_Queue**: The list of short-answer responses awaiting faculty review and marks assignment.
- **Badge**: A digital achievement awarded to a student for meeting defined performance or participation criteria.
- **Score_Badge**: A badge awarded based on the percentage score of a GRADED attempt: Gold (≥ 90%), Silver (75–89%), Bronze (60–74%).
- **Special_Badge**: A badge awarded for behavioral milestones: First Step, Perfect Score, Consistent Learner, Rising Star, Quiz Champion.
- **Best_Attempt**: When a student has multiple attempts on a quiz (max_attempts > 1), the attempt with the highest percentage. This is the only attempt used for dashboard statistics, leaderboard ranking, and badge evaluation.
- **Leaderboard**: A per-class, per-quiz ranked list showing each student's Best_Attempt score (first name + last initial only).
- **Notification**: An in-app message delivered to a user's bell icon and notifications page.
- **IST**: Indian Standard Time, UTC+5:30 (Asia/Kolkata). All datetime values are displayed and scheduled in IST.
- **UTC**: Coordinated Universal Time. All datetime values are stored in the database in UTC.
- **theme.js**: The file `frontend/src/theme.js`, which is the single source of truth for default branding values (colors, fonts, school name, logo placeholder).
- **school_settings**: The database table that stores Admin-configured runtime overrides for branding and feature flags.
- **Temporary_Password**: A system-generated plaintext password shown to Admin exactly once; stored only as a BCrypt hash and never persisted in plaintext.
- **RBAC**: Role-Based Access Control — the mechanism that restricts API endpoints and UI routes based on the authenticated user's role.
- **JWT**: JSON Web Token — the mechanism used for stateless authentication (access token 30 minutes, refresh token 7 days).
- **Flyway**: The database migration tool used to manage schema versioning.
- **Swagger_UI**: The auto-generated OpenAPI documentation interface available at the backend's `/swagger-ui.html` path.

---

## Requirements

### Requirement 1: User Account Management

**User Story:** As an Admin, I want to create, edit, and deactivate user accounts for all roles, so that only authorised individuals can access the portal.

#### Acceptance Criteria

1. THE User_Manager SHALL support four user roles: STUDENT, PARENT, FACULTY, and ADMIN.
2. WHEN an Admin creates a user account, THE User_Manager SHALL generate a Temporary_Password, store it as a BCrypt hash, set the must_change_password flag to true, and display the plaintext Temporary_Password to the Admin exactly once in a modal that requires explicit dismissal.
3. WHEN the Admin dismisses the Temporary_Password modal, THE User_Manager SHALL no longer expose the plaintext value; the Admin must reset the password to obtain a new one.
4. THE User_Manager SHALL store passwords exclusively as BCrypt hashes; plaintext passwords MUST NOT be persisted in the database, logs, or any other storage medium.
5. WHEN an Admin deactivates a user account, THE User_Manager SHALL set the is_active flag to false; the user's historical data SHALL be retained.
6. WHILE a user account has is_active set to false, THE Auth_Service SHALL reject all login attempts for that account with HTTP 401.
7. THE User_Manager SHALL enforce unique username and unique email (when provided) across all user records.
8. WHEN an Admin resets a user's password, THE User_Manager SHALL generate a new Temporary_Password, store it as a BCrypt hash, set must_change_password to true, and display the plaintext Temporary_Password to the Admin exactly once in a modal that requires explicit dismissal.
9. THE User_Manager SHALL allow Admin to create users one at a time via a form supplying full name, username, role, email (optional), phone (optional), and class (required for STUDENT role).
10. WHEN an Admin uploads a CSV file for bulk user creation, THE User_Manager SHALL validate each row against the required fields and role constraints, create valid accounts, and return a summary listing the count of successful creations and any rows that failed with the specific field-level error for each failure.
11. THE User_Manager SHALL provide a downloadable CSV template for each role (STUDENT, PARENT, FACULTY) so that Admins can prepare bulk-upload files in the correct format.
12. WHEN a bulk CSV file exceeds 5 MB, THE User_Manager SHALL reject the upload and return HTTP 400 with a descriptive error message.
13. WHEN a CSV row contains an invalid field value, THE User_Manager SHALL skip that row, record the error against the row number, and continue processing remaining rows.

---

### Requirement 2: Authentication and Session Management

**User Story:** As a user of any role, I want to log in with my credentials and have my session managed securely, so that my data is protected and I can navigate the portal without repeated logins.

#### Acceptance Criteria

1. THE Auth_Service SHALL provide a single login endpoint that accepts username or email and password for all four roles.
2. WHEN a user submits correct credentials, THE Auth_Service SHALL return a JWT access token (valid for 30 minutes) and a refresh token (valid for 7 days).
3. WHEN a user submits incorrect credentials, THE Auth_Service SHALL increment the failed_login_count for that account.
4. WHEN a user's failed_login_count reaches 5, THE Auth_Service SHALL set the locked_until field to the current UTC time plus 15 minutes and reject subsequent login attempts with HTTP 401 until the lockout period expires.
5. WHEN a user logs in successfully, THE Auth_Service SHALL reset the failed_login_count to 0.
6. WHEN a user with must_change_password set to true logs in, THE Auth_Service SHALL redirect that user to the forced password-change page before granting access to any other route.
7. THE Auth_Service SHALL enforce password rules: minimum 8 characters, at least 1 uppercase letter, at least 1 lowercase letter, and at least 1 digit.
8. WHEN a user submits a new password on the forced change page that does not meet the password rules, THE Auth_Service SHALL reject it and return a field-level error message specifying which rules were violated.
9. WHEN a valid refresh token is submitted to the refresh endpoint, THE Auth_Service SHALL issue a new access token and invalidate the used refresh token.
10. WHEN a user logs out, THE Auth_Service SHALL invalidate the refresh token server-side so it cannot be reused.
11. WHEN an access token expires and the client submits a valid refresh token, THE Auth_Service SHALL issue a new access token without requiring re-login.
12. WHEN an access token expires and no valid refresh token is available, THE Auth_Service SHALL return HTTP 401 and the frontend SHALL redirect the user to the login page with a session-expiry message.
13. THE Auth_Service SHALL apply rate limiting to the login endpoint; after 10 requests per minute from a single IP address, THE Auth_Service SHALL return HTTP 429.
14. THE System SHALL enforce RBAC on every API endpoint; a request from a role that does not have permission SHALL receive HTTP 403.
15. THE System SHALL enforce data-level authorization: Students SHALL only access their own data; Parents SHALL only access data for students linked to them; Faculty SHALL only access data for their Faculty_Assignments; Admins SHALL access all data.

---

### Requirement 3: Academic Structure Management

**User Story:** As an Admin, I want to manage academic years, classes, subjects, faculty assignments, and parent-student links, so that the portal reflects the school's organisational structure.

#### Acceptance Criteria

1. THE User_Manager SHALL allow Admin to create, edit, and delete Academic_Year records with a name, start date, and end date; exactly one Academic_Year may be marked as current at a time.
2. THE User_Manager SHALL allow Admin to create, edit, and delete Class records (e.g., "Class 6 - A") associated with an Academic_Year; Class names SHALL range from Class 1 to Class 12 with optional section labels.
3. THE User_Manager SHALL allow Admin to create, edit, and delete Subject records with a name and a unique code.
4. THE User_Manager SHALL allow Admin to create Faculty_Assignment records linking a Faculty member to a Class and Subject; each (faculty_user_id, class_id, subject_id) combination SHALL be unique.
5. THE User_Manager SHALL allow Admin to delete a Faculty_Assignment; deletion SHALL not affect historical quiz or attempt data.
6. THE User_Manager SHALL allow Admin to create parent-student links associating a Parent user with a Student; a Student MAY have up to two parent-student links; a Parent MAY be linked to multiple Students.
7. THE User_Manager SHALL allow Admin to delete a parent-student link without affecting historical data.
8. WHEN an Admin attempts to delete a Class that has Students enrolled, THE User_Manager SHALL return HTTP 409 and require explicit confirmation before proceeding.

---

### Requirement 4: Question Bank

**User Story:** As a Faculty member, I want to create, edit, and manage a bank of reusable questions for my assigned subjects and class levels, so that I can assemble quizzes efficiently.

#### Acceptance Criteria

1. THE Question_Bank SHALL support three question types: MCQ (single correct answer, 2 to 6 options), True/False, and Short_Answer.
2. WHEN a Faculty member creates a question, THE Question_Bank SHALL require: question text, type, subject, class level, difficulty (Easy, Medium, or Hard), and marks value (minimum 1).
3. WHEN a Faculty member creates a question, THE Question_Bank SHALL accept optional fields: topic or chapter, explanation (shown to students after results if enabled), image attachment (maximum 2 MB; JPEG, PNG, or GIF only), and model answer (for Short_Answer type only).
4. WHEN a Faculty member creates or edits a question, THE Question_Bank SHALL restrict the subject and class level to combinations present in that Faculty member's Faculty_Assignments; attempts to save a question outside assigned combinations SHALL be rejected with HTTP 403.
5. WHEN a Faculty member creates an MCQ question with fewer than 2 or more than 6 options, THE Question_Bank SHALL reject the request and return a field-level validation error.
6. WHEN a Faculty member creates an MCQ question with no option marked as correct, THE Question_Bank SHALL reject the request and return a field-level validation error.
7. THE Question_Bank SHALL allow Faculty to search and filter questions by subject, class level, topic, type, and difficulty.
8. WHEN a Faculty member edits a question that is referenced by a quiz with at least one attempt in IN_PROGRESS, SUBMITTED, or GRADED status, THE Question_Bank SHALL apply the edit only to the source question record in the bank; the Question_Snapshot associated with existing attempts SHALL remain unchanged.
9. WHEN a Faculty member deletes a question, THE Question_Bank SHALL set the is_active flag to false; the question SHALL NOT be deleted from the database; existing quiz references and attempt data SHALL be preserved.
10. THE Question_Bank SHALL allow Faculty to reuse an existing question in multiple quizzes.

---

### Requirement 5: Quiz Creation, Scheduling, and Publish Validation

**User Story:** As a Faculty member, I want to create, configure, schedule, and publish quizzes for my assigned classes and subjects, so that students can attempt them in a structured, time-bounded window.

#### Acceptance Criteria

1. THE Quiz_Engine SHALL require the following fields for a quiz: title, subject, at least one target Class section, questions list, duration in minutes, start_time (UTC), end_time (UTC), max_attempts (default 1), shuffle_questions (yes/no), shuffle_options (yes/no), show_answers setting (NEVER, AFTER_SUBMIT, or AFTER_END), and pass_percentage (default 40%).
2. THE Quiz_Engine SHALL compute total_marks automatically as the sum of marks values of all questions in the quiz.
3. THE Quiz_Engine SHALL enforce that a quiz subject and all target Class sections are within the creating Faculty member's Faculty_Assignments; attempts to save a quiz outside these combinations SHALL be rejected with HTTP 403.
4. WHEN a Faculty member attempts to publish a quiz (move it to SCHEDULED status), THE Quiz_Engine SHALL validate all of the following before accepting the request:
   a. The quiz has at least one question.
   b. The start_time is in the future at the moment of the publish request (evaluated in UTC).
   c. The end_time is strictly after the start_time.
   d. The quiz is assigned to at least one Class section.
   e. The quiz subject and all assigned Class sections are within the Faculty member's Faculty_Assignments.
   IF any condition is not met, THE Quiz_Engine SHALL reject the request with HTTP 400 and a descriptive error message identifying which condition failed.
5. THE Quiz_Engine SHALL transition quiz status automatically: a quiz is LIVE when the current UTC time is between start_time and end_time; a quiz is CLOSED when the current UTC time is after end_time; DRAFT and SCHEDULED are explicit states.
6. WHEN a quiz is in DRAFT or SCHEDULED status and no attempt exists for it, THE Quiz_Engine SHALL allow Faculty to edit all quiz fields.
7. WHEN a quiz has at least one attempt in IN_PROGRESS, SUBMITTED, or GRADED status, THE Quiz_Engine SHALL restrict edits to extending the end_time only; all other fields SHALL be locked.
8. THE Quiz_Engine SHALL allow Faculty to manually close a LIVE quiz before its end_time; this transitions the quiz to CLOSED status immediately.
9. THE Quiz_Engine SHALL allow Faculty to duplicate a CLOSED or SCHEDULED quiz as a new DRAFT with all fields copied and no attempts.
10. WHEN a Faculty member assigns a quiz to a Class section not in their Faculty_Assignments, THE Quiz_Engine SHALL reject the request with HTTP 403.
11. THE Quiz_Engine SHALL display quiz start_time and end_time to users in IST (UTC+5:30); all storage SHALL use UTC.

---

### Requirement 6: Quiz Attempt Engine

**User Story:** As a Student, I want to attempt a quiz within its scheduled window and have my progress preserved automatically, so that I can complete the assessment fairly even if my browser is interrupted.

#### Acceptance Criteria

1. WHEN a Student requests to start a quiz, THE Attempt_Engine SHALL verify that the current UTC time is between the quiz's start_time and end_time, that the Student belongs to a Class assigned to the quiz, and that the Student has not exhausted max_attempts; IF any condition is unmet, THE Attempt_Engine SHALL return HTTP 403 with a descriptive reason.
2. WHEN the Attempt_Engine creates an attempt record, THE Attempt_Engine SHALL record the started_at timestamp in UTC, compute the Effective_Deadline as the earlier of (started_at + duration_minutes) and the quiz end_time, and persist the Effective_Deadline server-side.
3. WHEN shuffle_questions is enabled for a quiz, THE Attempt_Engine SHALL generate the shuffled question order exactly once when the attempt record is created, persist the order server-side in the attempt record or a related table, and serve the same order on every subsequent request for that attempt.
4. WHEN shuffle_options is enabled for a quiz, THE Attempt_Engine SHALL generate the shuffled option order for each question exactly once when the attempt record is created, persist the order server-side, and serve the same order on every subsequent request for that attempt.
5. WHEN two students start the same quiz, THE Attempt_Engine SHALL generate independent shuffle orders for each student's attempt.
6. THE Attempt_Engine SHALL serve question data to the Student's browser ONE question at a time on request; correct answers, is_correct flags, and explanation text SHALL NOT be included in any response while the attempt status is IN_PROGRESS.
7. WHEN a Student saves an answer during an active attempt, THE Attempt_Engine SHALL persist only the changed answer (not the full attempt) and return a success confirmation.
8. WHEN a Student's browser is closed or refreshed during an IN_PROGRESS attempt, THE Attempt_Engine SHALL allow the Student to resume the same attempt by returning the current saved state, including the server-computed remaining time (Effective_Deadline minus current UTC time).
9. WHEN the current UTC time reaches the Effective_Deadline of an IN_PROGRESS attempt, THE Attempt_Engine SHALL automatically submit the attempt, set the status to SUBMITTED, and record the submitted_at timestamp in UTC.
10. THE Attempt_Engine SHALL run a scheduled job every minute that queries all IN_PROGRESS attempts whose Effective_Deadline is in the past and submits them.
11. WHEN a Student submits an attempt manually, THE Attempt_Engine SHALL present a confirmation dialog; WHEN the Student confirms, THE Attempt_Engine SHALL set the status to SUBMITTED and record submitted_at in UTC.
12. THE Attempt_Engine SHALL display the countdown timer to the Student based on the Effective_Deadline (not the raw quiz duration); the timer display SHALL use IST for any absolute time references.
13. THE Attempt_Engine SHALL change the timer display to amber color when 5 minutes remain and to red color when 1 minute remains.
14. THE Attempt_Engine SHALL display a question palette showing each question's state: answered, unanswered, or marked-for-review; and a progress bar showing the proportion of questions answered.
15. WHEN max_attempts is greater than 1 and a Student has multiple GRADED attempts for a quiz, THE Attempt_Engine SHALL designate the attempt with the highest percentage as the Best_Attempt for all dashboard statistics, leaderboard ranking, and badge evaluation.
16. WHEN a Question_Snapshot does not yet exist for a quiz at the moment the first attempt is created, THE Attempt_Engine SHALL create an immutable Question_Snapshot capturing the text, options, correct answer, and marks value of every question in the quiz; all subsequent scoring and display for that quiz SHALL use the Question_Snapshot.

---

### Requirement 7: Auto-Save Behavior

**User Story:** As a Student, I want my answers saved automatically as I navigate between questions, so that I do not lose my work due to accidental navigation or connectivity issues.

#### Acceptance Criteria

1. WHEN a Student navigates to a different question during an IN_PROGRESS attempt, THE Attempt_Engine SHALL auto-save the previously answered question's response in the background without blocking the navigation action.
2. THE Attempt_Engine SHALL transmit only the changed answer in the auto-save request; the full attempt state SHALL NOT be re-sent.
3. WHEN an auto-save request succeeds, THE System SHALL display a non-intrusive "Saved" indicator in the quiz UI that clears automatically.
4. WHEN an auto-save request fails, THE System SHALL display a non-intrusive "Save failed — retrying" indicator and retry the request using exponential backoff for up to 3 attempts.
5. WHEN all 3 auto-save retry attempts fail, THE System SHALL display a persistent warning message informing the Student that their last answer may not be saved and advising them not to navigate away; the warning SHALL remain visible until the next successful save.
6. THE auto-save indicator and retry mechanism SHALL NOT block the Student from navigating between questions or submitting the attempt.

---

### Requirement 8: Grading and Result Computation

**User Story:** As a Faculty member, I want to grade short-answer responses and have final scores computed automatically, so that students receive accurate results promptly.

#### Acceptance Criteria

1. WHEN an attempt is SUBMITTED and contains at least one Short_Answer question, THE Grading_Service SHALL set the grading_status of each Short_Answer response to PENDING and make the attempt available in the Grading_Queue.
2. THE Grading_Service SHALL display in the Grading_Queue all pending Short_Answer responses grouped by quiz; each response SHALL show the student's answer text, the model answer (if provided), and a marks input field (0 to the question's maximum marks) and an optional feedback text field.
3. WHEN a Faculty member assigns marks to all pending Short_Answer responses in an attempt, THE Grading_Service SHALL compute the final total_score as the sum of auto-graded marks and manually assigned marks, compute the percentage, set the attempt status to GRADED, evaluate badges via Badge_Service, and trigger notifications to the Student and linked Parents.
4. WHEN a Faculty member assigns marks to a Short_Answer response, THE Grading_Service SHALL use the Question_Snapshot marks value as the maximum allowed marks for that response.
5. THE Grading_Service SHALL auto-grade MCQ and True/False responses immediately upon submission using the Question_Snapshot correct answer; auto-graded responses SHALL receive grading_status AUTO_GRADED.
6. WHEN an attempt contains only MCQ or True/False questions, THE Grading_Service SHALL immediately set the attempt status to GRADED after submission, compute the total_score and percentage, evaluate badges, and notify the Student.
7. WHEN an attempt contains Short_Answer questions, THE System SHALL display "Partial result — pending review" to the Student until all Short_Answer responses in that attempt are GRADED.

---

### Requirement 9: Correct-Answer Visibility and API Security

**User Story:** As a system operator, I want correct answers to be hidden from students during active attempts and revealed only according to the quiz's show-answers setting, so that quiz integrity is maintained.

#### Acceptance Criteria

1. WHILE a quiz attempt has status IN_PROGRESS, THE Attempt_Engine SHALL exclude correct answers, is_correct flags, and explanation text from all API responses sent to the Student.
2. WHEN an attempt transitions to SUBMITTED or GRADED status and the quiz show_answers setting is NEVER, THE Attempt_Engine SHALL exclude correct answers, is_correct flags, and explanation text from the result response.
3. WHEN an attempt transitions to SUBMITTED or GRADED status and the quiz show_answers setting is AFTER_SUBMIT, THE Attempt_Engine SHALL include correct answers, is_correct flags, and explanation text in the result response immediately.
4. WHEN an attempt transitions to SUBMITTED or GRADED status and the quiz show_answers setting is AFTER_END, THE Attempt_Engine SHALL include correct answers, is_correct flags, and explanation text in the result response ONLY IF the current UTC time is after the quiz end_time; before end_time, these fields SHALL be excluded.
5. THE Quiz_Engine SHALL enforce these show_answers rules identically when a Student revisits a past result page; the visibility state SHALL be re-evaluated at each page request based on the current UTC time and the quiz end_time.
6. THE Attempt_Engine SHALL enforce correct-answer visibility server-side; the frontend SHALL NOT be relied upon as the sole enforcement mechanism.

---

### Requirement 10: Badges and Achievements

**User Story:** As a Student, I want to earn badges for my quiz performance and participation milestones, so that I am motivated to engage consistently with the portal.

#### Acceptance Criteria

1. THE Badge_Service SHALL evaluate and award badges ONLY on attempts with status GRADED; badges SHALL NOT be evaluated on SUBMITTED attempts with pending short-answer grading.
2. WHEN a GRADED attempt's percentage is 90 or above, THE Badge_Service SHALL award the Gold Score_Badge to the Student for that attempt.
3. WHEN a GRADED attempt's percentage is between 75 and 89 inclusive, THE Badge_Service SHALL award the Silver Score_Badge to the Student for that attempt.
4. WHEN a GRADED attempt's percentage is between 60 and 74 inclusive, THE Badge_Service SHALL award the Bronze Score_Badge to the Student for that attempt.
5. WHEN a Student completes their first GRADED quiz attempt, THE Badge_Service SHALL award the First Step Special_Badge.
6. WHEN a GRADED attempt's percentage is 100, THE Badge_Service SHALL award the Perfect Score Special_Badge.
7. WHEN a Student has completed 5 consecutive GRADED quiz attempts (across any subjects) each with a percentage of 60 or above, THE Badge_Service SHALL award the Consistent Learner Special_Badge.
8. WHEN a Student's percentage score in 3 consecutive GRADED attempts for the same subject shows a strictly increasing trend (each attempt's percentage is higher than the previous), THE Badge_Service SHALL award the Rising Star Special_Badge for that subject.
9. WHEN a quiz transitions to CLOSED status and all Short_Answer responses across all attempts for that quiz are in GRADED status, THE Badge_Service SHALL identify the highest percentage achieved among all Best_Attempts for that quiz; every Student whose Best_Attempt percentage equals that highest percentage SHALL receive the Quiz Champion Special_Badge.
10. WHEN two or more Students share the highest percentage for a quiz, THE Badge_Service SHALL award the Quiz Champion Special_Badge to ALL of those Students.
11. THE Badge_Service SHALL use only the Best_Attempt percentage when evaluating Score_Badges (Gold, Silver, Bronze) for a quiz where max_attempts is greater than 1.
12. WHEN a badge is awarded, THE Badge_Service SHALL record the badge, the associated quiz attempt id, and the awarded_at timestamp in UTC; the UI SHALL display the awarded_at in IST.
13. WHEN a new badge is earned by a Student, THE System SHALL display a celebration animation (confetti) and a modal; THE Notification_Service SHALL send an in-app notification to the Student.
14. THE Badge_Service SHALL allow Admin to configure the percentage thresholds for Gold, Silver, and Bronze Score_Badges via the Admin badge management page; updated thresholds SHALL apply to future badge evaluations only.
15. THE Badge_Service SHALL display unearned badges to the Student as greyed-out icons with the badge name visible, so that Students can see what they are working toward.

---

### Requirement 11: Student Dashboard and Quiz History

**User Story:** As a Student, I want a dashboard that shows my upcoming quizzes, recent results, performance trends, and badges, so that I can stay on top of my schedule and track my progress.

#### Acceptance Criteria

1. THE System SHALL display the Student dashboard with: a personalised greeting including the Student's name and class, the Student's most recently earned badge, upcoming quiz cards, recent results, overall average score, and total quizzes attempted.
2. WHEN computing the Student's average score and subject-wise performance on the dashboard, THE Analytics_Service SHALL use only the Best_Attempt percentage for each quiz.
3. THE System SHALL display upcoming quiz cards with: subject, start date and time in IST, end date and time in IST, and duration; the "Start Quiz" button SHALL be enabled only while the quiz status is LIVE.
4. THE System SHALL display a subject-wise performance bar chart and a score trend line chart using the Best_Attempt percentage for each quiz.
5. THE System SHALL display a badge shelf showing earned badges in colour and unearned badges as greyed-out icons.
6. THE System SHALL provide a quiz history page listing all past attempts with filters by subject and date; each history entry SHALL link to the attempt result page.
7. THE System SHALL provide a result page for each GRADED attempt showing: total score, percentage, pass or fail, time taken, and per-question review subject to the show_answers rules in Requirement 9.
8. WHEN a result contains Short_Answer questions not yet GRADED, THE System SHALL display "Partial result — pending review" and update the display to the final result after all responses are GRADED.
9. THE System SHALL provide a notifications page with a bell icon in the top bar showing the count of unread notifications; the Student SHALL be able to mark individual or all notifications as read.

---

### Requirement 12: Parent Dashboard

**User Story:** As a Parent, I want to view quiz schedules, scores, trends, and badges for each of my linked children, so that I can support their learning without needing to contact the school separately.

#### Acceptance Criteria

1. THE System SHALL display a child selector on the Parent dashboard when the Parent is linked to more than one Student; selecting a child SHALL update all displayed data to that child's records.
2. THE System SHALL display for the selected child: upcoming quizzes with subject, date, and time in IST; attempted quiz history with scores; average score; subject-wise performance bar chart; score trend line chart; and earned badges.
3. WHEN computing a child's average score and subject-wise performance on the Parent dashboard, THE Analytics_Service SHALL use only the Best_Attempt percentage for each quiz.
4. WHEN the Admin has enabled the leaderboard for the school, THE System SHALL display the selected child's class rank on the Parent dashboard.
5. THE System SHALL restrict Parent access so that a Parent can only view data for Students explicitly linked to them; requests for any other Student's data SHALL return HTTP 403.
6. THE System SHALL provide a notifications page for the Parent with a bell icon showing unread notification count; the Parent SHALL be able to mark individual or all notifications as read.

---

### Requirement 13: Faculty Dashboard and Analytics

**User Story:** As a Faculty member, I want a dashboard and analytics tools for my assigned classes and subjects, so that I can identify struggling students, difficult topics, and pending grading tasks efficiently.

#### Acceptance Criteria

1. THE System SHALL display the Faculty dashboard with: an overview of assigned classes and subjects, upcoming quizzes, and pending grading count.
2. THE System SHALL provide a quiz-wise report page for each quiz showing: average score, highest score, lowest score, pass percentage, score distribution chart, list of Students with individual scores, and list of Students who did not attempt.
3. WHEN computing quiz-wise statistics, THE Analytics_Service SHALL use the Best_Attempt score for each Student.
4. THE System SHALL provide a student-wise performance view within the Faculty's assigned subjects showing each Student's score trend over time using Best_Attempt percentages.
5. THE System SHALL provide a question-wise analysis view showing the percentage of Students who answered each question correctly across all attempts, to help Faculty identify difficult topics.
6. THE System SHALL allow Faculty to export a quiz report or student performance report to CSV format or PDF format.
7. THE System SHALL restrict Faculty analytics and report access to data within their Faculty_Assignments; requests for data outside assignments SHALL return HTTP 403.
8. THE Report_Service SHALL generate CSV exports using standard comma-separated format and PDF exports using a printable layout with the school name header.

---

### Requirement 14: Admin Dashboard and Analytics

**User Story:** As an Admin (Principal), I want a school-wide dashboard showing student progress, faculty activity, and key metrics, so that I can make informed decisions about academic performance and resource allocation.

#### Acceptance Criteria

1. THE System SHALL display the Admin dashboard with school overview cards: total active students, total active faculty, number of quizzes conducted in the current month, and the overall average score across all classes.
2. THE System SHALL provide a class-wise progress view showing average score per class and per subject, trend over time, and the top 5 and bottom 5 performing Students filterable by class and section.
3. WHEN computing class-wise and school-wide averages, THE Analytics_Service SHALL use only the Best_Attempt percentage for each Student per quiz.
4. THE System SHALL allow Admin to drill down to a specific Student's full performance profile including all quiz attempts, scores, subject-wise chart, and earned badges.
5. THE System SHALL provide a faculty progress view showing for each Faculty member: number of quizzes created, scheduled, and conducted; average student score across their quizzes; student participation rate; and pending grading count.
6. THE System SHALL allow Admin to export class-wise progress or faculty progress reports to CSV or PDF format.

---

### Requirement 15: Notifications

**User Story:** As a user of any role, I want to receive timely in-app notifications about quiz events and results, so that I am informed without having to check the portal manually.

#### Acceptance Criteria

1. THE Notification_Service SHALL create in-app notifications for the following triggers: new quiz scheduled (sent to Students and linked Parents of targeted classes), quiz starting in 24 hours (sent to Students and linked Parents), quiz starting in 1 hour (sent to Students and linked Parents), result published or attempt GRADED (sent to the Student and linked Parents), new badge earned (sent to the Student), and new account created or password reset (sent to the affected user).
2. THE Notification_Service SHALL schedule reminder notifications (24-hour and 1-hour before quiz start) using a background job; all scheduling calculations SHALL use UTC internally and display times in IST.
3. THE System SHALL display notifications in a bell icon with an unread count badge; clicking the bell SHALL open a notifications panel or page listing all notifications with title, message, timestamp in IST, and read or unread status.
4. THE System SHALL allow a user to mark an individual notification as read or mark all notifications as read in a single action.
5. WHERE the Admin has enabled email notifications in school_settings, THE Notification_Service SHALL send an email for each of the triggers listed in criterion 1 to the user's registered email address.
6. WHEN email notifications are disabled in school_settings, THE Notification_Service SHALL skip email sending; in-app notifications SHALL still be created.
7. THE Notification_Service SHALL not block quiz or grading operations; notification creation failures SHALL be logged and SHALL NOT cause the triggering operation to fail.

---

### Requirement 16: Leaderboard

**User Story:** As a Student, I want to see a class leaderboard for each quiz, so that I can understand my standing relative to classmates.

#### Acceptance Criteria

1. WHEN the Admin has enabled the leaderboard in school_settings, THE System SHALL display a per-class, per-quiz leaderboard showing each Student's rank, first name plus last-name initial only, and Best_Attempt percentage score.
2. WHEN the Admin has disabled the leaderboard in school_settings, THE System SHALL hide the leaderboard from Student and Parent views entirely.
3. THE System SHALL rank Students by Best_Attempt percentage in descending order; Students with equal percentages SHALL share the same rank.
4. THE System SHALL restrict a Student to viewing only the leaderboard for their own class; cross-class leaderboard data SHALL return HTTP 403.
5. THE System SHALL display the Parent's linked child's rank on the leaderboard when the leaderboard is enabled.

---

### Requirement 17: Branding, Theme, and School Settings

**User Story:** As an Admin, I want to configure the school's name, logo, and colours from the portal without requiring a code rebuild, so that the portal reflects the school's official branding when assets are available.

#### Acceptance Criteria

1. THE System SHALL store default branding values (primary color #2563EB, secondary color #F59E0B, school name "Nexus Ranaji English Medium School", a placeholder logo, and the Nunito or Poppins font) exclusively in `frontend/src/theme.js`; no color, font, or school-name value SHALL be hard-coded in any other file.
2. WHEN the frontend application loads, THE System SHALL fetch runtime branding overrides from the school_settings API endpoint and apply them on top of the theme.js defaults without requiring a code rebuild or redeployment.
3. THE System SHALL allow Admin to update the school name, primary color, secondary color, and logo image on the Admin School Settings page; updates SHALL be persisted in the school_settings database table.
4. WHEN Admin uploads a logo image, THE School_Settings_Service SHALL validate that the file is JPEG, PNG, or GIF and does not exceed 2 MB; non-compliant uploads SHALL be rejected with HTTP 400 and a descriptive error message.
5. THE System SHALL allow Admin to toggle the leaderboard on or off for the whole school from the School Settings page; the change SHALL take effect immediately for all subsequent page loads.
6. THE System SHALL allow Admin to toggle email notifications on or off from the School Settings page; the change SHALL take effect immediately for subsequent notification events.
7. THE System SHALL apply an audit log entry whenever Admin changes school settings, recording the Admin's user id, the field changed, the old value, the new value, and the UTC timestamp.

---

### Requirement 18: UI Layout and Navigation

**User Story:** As a user of any role, I want a consistent, role-appropriate navigation layout that works on mobile and desktop, so that I can find any page quickly regardless of device.

#### Acceptance Criteria

1. THE System SHALL display a left sidebar with icons and labels as the primary navigation for all four roles (Student, Parent, Faculty, Admin) on desktop screens (width ≥ 1024 px).
2. THE System SHALL display a bottom navigation bar for Student and Parent roles on mobile screens (width < 1024 px).
3. THE System SHALL display a hamburger drawer for Faculty and Admin roles on mobile screens (width < 1024 px).
4. THE System SHALL display breadcrumbs on all pages that are more than one level deep in the navigation hierarchy; every deep page SHALL include a back navigation control.
5. THE System SHALL display a top bar on all authenticated pages containing: the school name, a notification bell icon with unread count, and a profile menu with the user's name and logout option.
6. THE System SHALL be fully responsive and usable on screens with a minimum width of 360 px.
7. THE System SHALL use the "Nunito" or "Poppins" font (loaded via Google Fonts) for all headings and body text.
8. THE System SHALL use large, touch-friendly buttons (minimum 44 × 44 px tap target) and generous spacing throughout the Student and Parent interfaces.
9. THE System SHALL display a loading skeleton or spinner, an empty-state message, and an error-state message with friendly text on every list page and data-driven page.
10. THE System SHALL use rounded cards (rounded-2xl), soft shadows, and the branding colors defined in theme.js (with runtime overrides applied) consistently across all pages.
11. THE System SHALL maintain good color contrast ratios (WCAG AA minimum), visible keyboard focus indicators, alt text on all images, associated labels on all form inputs, and inline error messages adjacent to invalid fields.

---

### Requirement 19: Quiz-Taking Screen

**User Story:** As a Student, I want a distraction-free quiz screen that shows one question at a time with clear navigation and a visible timer, so that I can focus entirely on answering.

#### Acceptance Criteria

1. THE System SHALL display one question at a time on the quiz-taking screen with large readable text and large tappable answer option buttons.
2. THE System SHALL display a countdown timer showing the time remaining to the Effective_Deadline, not the raw quiz duration.
3. THE System SHALL display the timer in amber color when 5 minutes remain and in red color when 1 minute remains.
4. THE System SHALL display a progress bar indicating the proportion of questions answered out of total questions.
5. THE System SHALL display a question palette showing the status of each question as answered, unanswered, or marked-for-review.
6. THE System SHALL provide Previous and Next navigation buttons and a "Mark for review" toggle button on every question screen.
7. THE System SHALL display a distraction-free layout during quiz-taking with no sidebar, no unrelated links, and minimal chrome.
8. WHEN a Student earns a Gold result or a new badge, THE System SHALL display a confetti celebration animation; the animation SHALL complete within 3 seconds and SHALL NOT prevent further interaction.

---

### Requirement 20: Datetime Handling (UTC Storage, IST Display)

**User Story:** As a system operator, I want all datetime values stored in UTC and displayed in IST, so that the portal behaves correctly regardless of the server's configured timezone.

#### Acceptance Criteria

1. THE System SHALL store all datetime values (including quiz start_time, end_time, attempt started_at, submitted_at, notification timestamps, badge awarded_at, and audit log timestamps) in the database in UTC.
2. THE System SHALL display all datetime values to users in the Asia/Kolkata timezone (IST, UTC+5:30).
3. THE System SHALL perform all datetime conversions explicitly in application code; THE System SHALL NOT rely on the server operating system timezone or database session timezone settings.
4. THE Notification_Service SHALL schedule reminder jobs (24-hour and 1-hour before quiz start) by computing the trigger time in UTC from the quiz start_time; IST conversion SHALL be applied only for display in notification message text.
5. WHEN the Attempt_Engine computes the Effective_Deadline, THE Attempt_Engine SHALL perform the calculation in UTC and persist the result in UTC.

---

### Requirement 21: Security and Non-Functional Properties

**User Story:** As a system operator, I want the portal to meet baseline security and performance standards, so that student data is protected and the system remains usable under normal school load.

#### Acceptance Criteria

1. THE Auth_Service SHALL hash all passwords using BCrypt before storage; plaintext passwords SHALL NOT appear in the database, logs, application memory dumps, or API responses.
2. THE System SHALL read the JWT secret from an environment variable; the secret SHALL NOT be hard-coded in source code or committed to version control.
3. THE System SHALL validate and escape all user-supplied input to prevent XSS attacks; THE System SHALL use JPA parameterized queries to prevent SQL injection.
4. THE System SHALL restrict CORS to the configured FRONTEND_URL environment variable value; requests from other origins SHALL be rejected.
5. THE System SHALL validate uploaded files (images and CSV) for MIME type and size before processing; images SHALL be rejected if larger than 2 MB or not JPEG, PNG, or GIF; CSV files SHALL be rejected if larger than 5 MB.
6. THE System SHALL record an audit log entry for the following Admin actions: user creation, user deactivation, password reset, faculty assignment changes, parent-student link changes, and school settings changes; each entry SHALL include the Admin user id, action type, affected entity id, and UTC timestamp.
7. THE System SHALL respond to common authenticated API requests (dashboard load, quiz list, attempt answer save) within 500 ms under normal load.
8. THE System SHALL support at least 300 concurrent Students submitting quiz answers simultaneously; auto-save requests SHALL update only the changed answer record to minimise database write load.
9. THE System SHALL paginate all list API endpoints using page and size parameters and return total record counts in the response envelope.
10. THE System SHALL use database indexes on quiz_attempts (student_id, quiz_id), quizzes (start_time), notifications (user_id, is_read), and students (class_id).
11. THE System SHALL use a stateless backend architecture (JWT-based, no server-side session state) so that multiple backend instances can be run without shared session storage.
12. THE System SHALL run database schema migrations via Flyway; schema changes SHALL NOT be applied via JPA ddl-auto=update in production profiles.

---

### Requirement 22: Seed Data and Development Profile

**User Story:** As a developer, I want a realistic set of seed data loaded automatically in the development profile, so that I can verify all dashboards and charts without manually creating data.

#### Acceptance Criteria

1. WHEN the application starts with the "dev" Spring profile active, THE System SHALL load seed data comprising: 1 Admin user, 3 Faculty users, 2 Classes (Class 6-A and Class 7-A), 5 Subjects, 10 Students, 6 Parents (some linked to two Students), 30 Questions covering all three types (MCQ, True/False, Short_Answer) and multiple difficulty levels, and 4 Quizzes (one upcoming, one live, two closed) with at least a few attempts and results so that all charts and dashboard cards show data.
2. WHEN the application starts with the "dev" profile active, THE System SHALL pre-load the default badge rules (Gold ≥ 90%, Silver 75–89%, Bronze 60–74%) into the badges table if they do not already exist.
3. WHEN the application starts without the "dev" profile, THE System SHALL NOT load seed data.
4. WHEN the application starts for the first time in any profile, THE System SHALL create one default Admin account using credentials from the ADMIN_DEFAULT_USERNAME and ADMIN_DEFAULT_PASSWORD environment variables; the password SHALL be stored as a BCrypt hash with must_change_password set to true.

---

### Requirement 23: API Design and Documentation

**User Story:** As a developer, I want a well-documented, consistently structured API, so that frontend integration and third-party testing are straightforward.

#### Acceptance Criteria

1. THE System SHALL prefix all API endpoints with `/api/v1` and return responses in the standard envelope `{ "success": true|false, "message": "...", "data": {...} }`.
2. THE System SHALL use HTTP status codes consistently: 200 for successful reads and updates, 201 for successful resource creation, 400 for validation errors, 401 for unauthenticated requests, 403 for authorisation failures, 404 for not-found resources, 409 for conflict errors, and 500 for unexpected server errors.
3. THE System SHALL return field-level validation errors in the 400 response body, identifying each invalid field by name and providing a human-readable reason.
4. THE System SHALL expose a Swagger_UI interface at `/swagger-ui.html` documenting all endpoints, request schemas, response schemas, and required security headers.
5. THE System SHALL use a global exception handler (@ControllerAdvice) that catches all unhandled exceptions and returns a structured 500 response with a friendly message; stack traces SHALL NOT be included in API responses in production profiles.
6. WHEN a Student requests question data for an IN_PROGRESS attempt, THE Attempt_Engine SHALL omit correct answer fields, is_correct flags, and explanation text from the API response (enforced server-side, not just client-side).

---

### Requirement 24: Out of Scope

**User Story:** As a project stakeholder, I want to clearly define what is not being built in version 1, so that scope boundaries are understood and no effort is wasted on excluded features.

#### Acceptance Criteria

1. THE System SHALL NOT implement online payments, fees management, or any financial transaction functionality.
2. THE System SHALL NOT implement live classes, video calls, audio calls, or any real-time chat or messaging between users.
3. THE System SHALL NOT implement AI-powered features including AI question generation, AI proctoring, or automated grading of Short_Answer responses.
4. THE System SHALL NOT implement camera-based or screen-recording proctoring, or tab-switch detection during quiz attempts.
5. THE System SHALL NOT implement attendance tracking, timetable management, homework assignment, or report-card generation modules.
6. THE System SHALL NOT provide native iOS or Android mobile applications; mobile support SHALL be delivered exclusively through the responsive web application.
7. THE System SHALL NOT send SMS or WhatsApp notifications; all push communication SHALL be limited to in-app notifications and optional email.
8. THE System SHALL NOT implement multi-school (multi-tenant) functionality; the portal serves a single school instance.
9. THE System SHALL NOT implement multi-language support; all UI text SHALL be in English.
10. THE System SHALL NOT implement question types beyond MCQ, True/False, and Short_Answer; types such as match-the-following, fill-in-the-blank, and essay are excluded from version 1.
