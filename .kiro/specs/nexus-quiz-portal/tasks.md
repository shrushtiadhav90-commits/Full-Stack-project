# Implementation Plan: Nexus Quiz Portal

## Overview

Full-stack implementation of the Nexus Quiz Portal for Nexus Ranaji English Medium School. The portal replaces paper-based quiz management with a structured digital platform supporting four roles (Student, Parent, Faculty, Admin) backed by a Spring Boot 3.3.4 REST API and a React 18 + Vite SPA. The nine phases below follow the design build order exactly and incorporate all amendments A1–A14.

---

## Tasks

### Phase 1 — Project Setup

- [ ] 1. Initialize repository and project scaffolding
  - [ ] 1.1 Initialize Git repository with `.gitignore` (exclude `*.env`, `target/`, `node_modules/`, `UPLOAD_DIR/`, `*.class`, `.idea/`, `.vscode/`)
    - Create root `README.md` skeleton with prerequisites, startup steps, default admin credentials note, Swagger UI URL, and Postman import instructions
    - Create `.env.example` listing every backend and frontend environment variable key: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRY_MINUTES`, `JWT_REFRESH_EXPIRY_DAYS`, `FRONTEND_URL`, `UPLOAD_DIR`, `ADMIN_DEFAULT_USERNAME`, `ADMIN_DEFAULT_PASSWORD`, `app.cookie.secure`, `VITE_API_BASE_URL`
    - Create `docker-compose.yml` for MySQL 8 container with `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` env vars and `--default-time-zone=+00:00` command flag (serverTimezone=UTC)
    - _Requirements: 21.12, 22.4_

  - [ ] 1.2 Create Spring Boot Maven project (`pom.xml`) with all pinned dependencies (A11)
    - Spring Boot parent: `3.3.4`; Java source/target: `21`
    - Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-mail` (optional), `spring-boot-starter-test`
    - `jjwt-api`, `jjwt-impl`, `jjwt-jackson` all at `0.12.6`
    - `bucket4j-core` at `8.7.0`
    - `opencsv` at `5.9`
    - `openpdf` at `2.0.2`
    - `jqwik` at `1.8.4` (test scope)
    - `tika-core` at `2.9.2`
    - `flyway-core` and `flyway-mysql`
    - `mysql-connector-j`
    - `lombok`
    - `springdoc-openapi-starter-webmvc-ui`
    - Testcontainers BOM `1.19.8`; `testcontainers` and `testcontainers-mysql` (test scope)
    - Maven Surefire plugin: add `<argLine>-Duser.timezone=UTC</argLine>`
    - Spring Boot Maven plugin: add `<jvmArgument>-Duser.timezone=UTC</jvmArgument>`
    - _Requirements: 21.11_

  - [ ] 1.3 Create `NexusQuizApplication.java` main class and core `application.yml`
    - `main()` calls `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` as the very first line before `SpringApplication.run(...)` (A11)
    - `application.yml`: `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`, `spring.datasource.url` includes `serverTimezone=UTC&useUnicode=true&characterEncoding=utf8`, `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, `server.error.include-stacktrace=never`, `spring.flyway.enabled=true`
    - `application-dev.yml`: `app.cookie.secure=false`, `logging.level.org.hibernate.SQL=DEBUG`, `logging.level.org.hibernate.orm.jdbc.bind=TRACE`, `spring.jpa.hibernate.ddl-auto=validate` (keep validate, not create-drop)
    - `application-prod.yml`: `app.cookie.secure=true`, `spring.jpa.hibernate.ddl-auto=validate`
    - _Requirements: 20.1, 20.3, 21.11, 21.12_

  - [ ] 1.4 Write `V1__init_schema.sql` Flyway migration
    - Create all tables with exact columns, types, and constraints from the design ER diagram: `users`, `academic_years`, `classes`, `subjects`, `students`, `parent_student`, `faculty_assignments`, `questions`, `question_options`, `quizzes`, `quiz_classes`, `quiz_questions`, `quiz_question_snapshots`, `quiz_attempts`, `attempt_answers`, `badges`, `student_badges`, `notifications`, `refresh_tokens`, `school_settings`, `audit_log`
    - All datetime columns: `DATETIME(6)` stored in UTC
    - Add all indexes listed in design: `quiz_attempts(student_id, quiz_id)`, `quiz_attempts(effective_deadline_utc, status)`, `quizzes(start_time_utc)`, `quizzes(status, start_time_utc)`, `notifications(user_id, is_read)`, `students(class_id)`, `refresh_tokens(token_hash)`, `student_badges(student_id, badge_id)`, `attempt_answers(attempt_id, quiz_question_id)`, `audit_log(admin_user_id, created_at_utc)`
    - Add all unique constraints from design
    - _Requirements: 21.10, 21.12_

  - [ ] 1.5 Write `V2__seed_badges.sql` Flyway migration (A3)
    - Insert all 8 badge rows: Gold (`SCORE`, `min_percentage=90`, icon `gold.svg`), Silver (`SCORE`, `min_percentage=75`, icon `silver.svg`), Bronze (`SCORE`, `min_percentage=60`, icon `bronze.svg`), First Step (`SPECIAL`, `min_percentage=0`, icon `first-step.svg`), Perfect Score (`SPECIAL`, `min_percentage=100`, icon `perfect-score.svg`), Consistent Learner (`SPECIAL`, `min_percentage=60`, icon `consistent-learner.svg`), Rising Star (`SPECIAL`, `min_percentage=0`, icon `rising-star.svg`), Quiz Champion (`SPECIAL`, `min_percentage=0`, icon `quiz-champion.svg`)
    - Use `INSERT IGNORE` so the migration is idempotent
    - _Requirements: 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9_

  - [ ] 1.6 Create Vite React frontend scaffold and install all pinned dependencies (A11)
    - `npm create vite@latest frontend -- --template react`
    - Pin in `package.json`: `react@18.3.1`, `react-dom@18.3.1`, `react-router-dom@6.26.1`, `axios@1.7.4`, `react-hook-form@7.53.0`, `recharts@2.12.7`, `lucide-react@0.441.0`, `canvas-confetti@1.9.3`, `react-hot-toast@2.4.1`
    - Dev dependencies: `tailwindcss@3.4.10`, `autoprefixer`, `postcss`, `@types/canvas-confetti`
    - Run `npx tailwindcss init -p` to generate `tailwind.config.js` and `postcss.config.js`
    - _Requirements: 18.7_

  - [ ] 1.7 Configure `tailwind.config.js`, `theme.js`, and `index.css` with RGB CSS variable color tokens (A3, should-fix)
    - `frontend/src/theme.js`: export `defaultColors` object and `toCssVars()` function; export colors as both hex values AND RGB channel CSS variables (e.g., `--color-primary-rgb: 37, 99, 235`) so Tailwind opacity modifiers work
    - `tailwind.config.js`: map Tailwind color tokens using `rgb(var(--color-primary-rgb) / <alpha-value>)` syntax for opacity modifier support; add `fontFamily.sans: ['Nunito', 'Poppins', 'ui-sans-serif', 'system-ui']`; add `borderRadius['2xl']: '1rem'`; set `content: ['./src/**/*.{js,jsx}']`
    - `frontend/src/index.css`: define all CSS custom properties in `:root` for both hex and RGB channel variants as defaults (used before ThemeContext initializes)
    - _Requirements: 17.1, 18.7, 18.10_

  - [ ] 1.8 Create placeholder SVG badge icon files (A3)
    - Create `frontend/src/assets/badges/` directory
    - Create 8 placeholder SVG files: `gold.svg`, `silver.svg`, `bronze.svg`, `first-step.svg`, `perfect-score.svg`, `consistent-learner.svg`, `rising-star.svg`, `quiz-champion.svg`
    - Each SVG is a visually distinct colored circle or shield shape with a letter/symbol label (e.g., "G" for gold) as a design placeholder
    - _Requirements: 10.13, 10.15_

- [ ] 2. Phase 1 Checkpoint
  - Verify Maven build compiles cleanly (`mvn compile`); Flyway migrations apply against the Docker MySQL container with no errors; frontend `npm run build` succeeds; all 8 badge SVGs are present; `tailwind.config.js` RGB tokens render correctly in a test component

---

### Phase 2 — Database + Authentication

- [ ] 3. Implement JPA entities, utilities, and security infrastructure
  - [ ] 3.1 Create core JPA entities: `User`, `RefreshToken`, `AcademicYear`, `Class`, `Subject`
    - All datetime fields as `java.time.Instant`; never `java.util.Date` or `java.sql.Timestamp`
    - `User` entity: `id`, `fullName`, `username`, `email`, `phone`, `passwordHash` (`@JsonIgnore`), `role` (enum: STUDENT, PARENT, FACULTY, ADMIN), `isActive`, `mustChangePassword`, `failedLoginCount`, `lockedUntilUtc`, `profilePhotoUrl`, `createdAtUtc`, `updatedAtUtc`
    - `RefreshToken` entity: `id`, `userId` (FK), `tokenHash` (UK), `expiresAtUtc`, `revoked`
    - `AcademicYear`, `Class`, `Subject` entities with all columns from V1 schema
    - _Requirements: 1.4, 2.7, 3.1, 3.2, 3.3_

  - [ ] 3.2 Implement `DateTimeUtil` and `PasswordUtil` utility classes
    - `DateTimeUtil.toIst(Instant)`: always uses `ZoneId.of("Asia/Kolkata")`, never `ZoneId.systemDefault()`; returns `ZonedDateTime`
    - `DateTimeUtil.toIstString(Instant)`: formats as ISO 8601 with offset `+05:30`
    - `DateTimeUtil.fromIst(ZonedDateTime)`: converts to `Instant` in UTC
    - `PasswordUtil.hash(plaintext)`: uses `BCryptPasswordEncoder(strength=12)`; single place where BCrypt is called
    - `PasswordUtil.verify(plaintext, hash)`: delegates to `BCryptPasswordEncoder.matches()`
    - `PasswordUtil.generate()`: random 12-character alphanumeric temporary password; returned to caller, never stored in plaintext
    - _Requirements: 1.2, 1.4, 20.2, 20.3_

  - [ ] 3.3 Implement `JwtService`, `JwtFilter`, and `UserDetailsServiceImpl`
    - `JwtService`: generate access token (HS256, 30-min expiry, claims: `sub`=userId, `role`, `iat`, `exp`; secret from `JWT_SECRET` env var); validate token; extract claims. Use jjwt `0.12.6` API (`Jwts.builder()`, `Jwts.parser()`)
    - `JwtFilter` extends `OncePerRequestFilter`: extract `Authorization: Bearer` header; validate token; set `SecurityContext`. Skip filter for public endpoints (`/api/v1/auth/login`, `/api/v1/auth/refresh`)
    - `UserDetailsServiceImpl` implements `UserDetailsService`: load by username or email; prefix role with `ROLE_` (e.g., `ROLE_ADMIN`) in `GrantedAuthority`
    - _Requirements: 2.2, 2.9, 2.11, 21.2_

  - [ ] 3.4 Implement `RefreshTokenService` and `RateLimitingFilter`
    - `RefreshTokenService`: generate opaque UUID token; store SHA-256 hash in `refresh_tokens`; validate (check hash, expiry, not revoked); revoke (set `revoked=true`); cleanup expired tokens. Cookie expiry = `JWT_REFRESH_EXPIRY_DAYS × 86400` seconds (A5)
    - `RateLimitingFilter` (Bucket4j, A6): per-IP limit (20 req/min) on `POST /api/v1/auth/login`; use `X-Forwarded-For` first IP if present, else `getRemoteAddr()`; store buckets in `ConcurrentHashMap<String, Bucket>`; return HTTP 429 + `Retry-After: 60` header when exceeded
    - Per-IP check fires BEFORE any DB lookup (A6)
    - _Requirements: 2.3, 2.4, 2.13_

  - [ ] 3.5 Implement `SecurityConfig` with RBAC, CORS, and custom error handlers (A4)
    - `@EnableWebSecurity` + `@EnableMethodSecurity` (NOT deprecated `@EnableGlobalMethodSecurity`)
    - Public endpoints (no auth required): `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `GET /files/logo/**` (A4)
    - All other `/api/v1/**` require authentication
    - All authorities use `ROLE_` prefix; `@PreAuthorize` uses `hasRole('ADMIN')` etc.
    - `AuthenticationEntryPoint` bean: returns JSON `{ "success": false, "message": "Authentication required" }` HTTP 401 (not HTML redirect) (A4)
    - `AccessDeniedHandler` bean: returns JSON `{ "success": false, "message": "Access denied" }` HTTP 403 (A4)
    - CORS: `allowedOrigins=[${FRONTEND_URL}]`, `allowedMethods=[GET, POST, PUT, PATCH, DELETE, OPTIONS]`, `allowedHeaders=[Authorization, Content-Type]`, `allowCredentials=true`; never `"*"` origin
    - Stateless session (`SessionCreationPolicy.STATELESS`)
    - Register `JwtFilter` before `UsernamePasswordAuthenticationFilter`; register `RateLimitingFilter` before `JwtFilter`
    - _Requirements: 2.14, 2.15, 21.3, 21.4_

  - [ ] 3.6 Implement `GlobalExceptionHandler` (`@ControllerAdvice`) and `ApiResponse<T>` envelope
    - `ApiResponse<T>` record/class: `success`, `message`, `data`, `errors`
    - Handle: `MethodArgumentNotValidException` (400, field errors), `ConstraintViolationException` (400), `EntityNotFoundException` (404), `DuplicateEntityException` (409), `AuthorizationException` (403), `AuthenticationException` (401), `RateLimitExceededException` (429 + `Retry-After`), `FileSizeExceededException` (400), `InvalidFileTypeException` (400), `AttemptWindowClosedException` (403), `QuizPublishValidationException` (400), generic `Exception` (500 — log full stack trace server-side, return friendly message only; no stack trace in prod)
    - _Requirements: 23.1, 23.2, 23.3, 23.5_

  - [ ] 3.7 Implement `AuthController` and `AuthService`
    - `POST /api/v1/auth/login`: validate credentials; per-IP rate limit fires first; check `isActive`; check `lockedUntil`; verify BCrypt; on failure increment `failedLoginCount` (lock after 5); on success reset `failedLoginCount`, issue JWT + refresh token; set `HttpOnly SameSite=Strict` cookie with `Path=/api/v1/auth`, `Secure` flag from `app.cookie.secure` property, `Max-Age` from `JWT_REFRESH_EXPIRY_DAYS` (A5)
    - `POST /api/v1/auth/refresh`: read refresh token from `HttpOnly` cookie; validate; issue new access token; rotate refresh token (revoke old, issue new); update cookie
    - `POST /api/v1/auth/logout`: revoke refresh token; clear cookie
    - `POST /api/v1/auth/change-password`: validate old password; enforce password rules (8+ chars, 1 uppercase, 1 lowercase, 1 digit); hash new password; set `mustChangePassword=false`
    - `GET /api/v1/auth/me`: return current user info (id, fullName, role, mustChangePassword)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 2.11, 2.12_

  - [ ] 3.8 Implement `AdminSeeder` `ApplicationRunner` (A1)
    - `@Component` implementing `ApplicationRunner`; runs at startup in ALL profiles
    - Check if any user with `role=ADMIN` exists; if yes, skip
    - Read `ADMIN_DEFAULT_USERNAME` and `ADMIN_DEFAULT_PASSWORD` env vars
    - Create admin user: hash password with `PasswordUtil.hash()`, set `mustChangePassword=true`, `isActive=true`
    - Log "Default admin created" at INFO level (never log the password)
    - _Requirements: 22.4_

  - [ ] 3.9 Implement frontend `AuthContext`, `axiosInstance`, `ProtectedRoute`, and auth pages
    - `axiosInstance.js`: `baseURL=VITE_API_BASE_URL`, `withCredentials=true`; request interceptor attaches `Authorization: Bearer <accessToken>`; response 401 interceptor queues concurrent 401s, calls `refreshTokenApi()` (outside queue), retries on success, dispatches logout on failure; skip interceptor for `/auth/refresh`
    - `AuthContext.jsx`: on app load call `POST /api/v1/auth/refresh` directly (NOT via Axios interceptor) to restore session silently; store `accessToken` in memory only (never `localStorage`); expose `user`, `login()`, `logout()`, `isAuthenticated`
    - `ProtectedRoute.jsx`: redirect to `/login` if not authenticated; redirect to `/change-password` if `mustChangePassword=true`; redirect to `/unauthorized` if role mismatch
    - `LoginPage.jsx`: React Hook Form; submit to `POST /api/v1/auth/login`; show field errors; handle lockout message
    - `ChangePasswordPage.jsx`: enforce password rules client-side; submit to `POST /api/v1/auth/change-password`
    - `AppRouter.jsx`: define all routes with `ProtectedRoute` wrappers per role; include `/unauthorized` and `/404` public pages
    - _Requirements: 2.1, 2.6, 2.7, 2.8, 2.11, 2.12_

  - [ ] 3.10 Write JUnit 5 unit tests for `JwtService`, `PasswordUtil`, `DateTimeUtil`, and `AuthService`
    - `JwtService`: test token generation, validation, expiry, and claim extraction
    - `PasswordUtil`: test BCrypt hash prefix (`$2a$` or `$2b$`), `verify()` round-trip, `generate()` length and charset
    - `DateTimeUtil`: test `toIst()` always uses Asia/Kolkata, `fromIst(toIst(instant))` round-trip identity, never `ZoneId.systemDefault()`
    - `AuthService`: test lockout after 5 failures, reset on success, reject inactive users, reject before lockout expires
    - _Requirements: 1.4, 2.3, 2.4, 2.7, 20.3_

  - [ ]* 3.11 Write jqwik property test for Property 9 (Temporary Password Never Persisted or Logged)
    - `@Property(tries=200) @Tag("Feature: nexus-quiz-portal, Property 9: Temporary Password Never Persisted or Logged")`
    - For any generated password string `p`: `BCryptPasswordEncoder.matches(p, PasswordUtil.hash(p))` is true; `hash.startsWith("$2a$") || hash.startsWith("$2b$")` is true
    - For any generated password string `p`: assert `PasswordUtil.hash(p)` does not contain `p` as a substring
    - _Requirements: 1.2, 1.3, 1.4, 1.8, 2.1_

  - [ ]* 3.12 Write jqwik property test for Property 10 (UTC-Only Storage Invariant)
    - `@Property(tries=200) @Tag("Feature: nexus-quiz-portal, Property 10: UTC-Only Storage Invariant")`
    - For any generated `ZonedDateTime` in IST: `DateTimeUtil.toIst(instant).toInstant() == instant` (round-trip identity)
    - Assert `DateTimeUtil.toIst(instant)` zone is always `Asia/Kolkata` and never system default
    - _Requirements: 20.1, 20.3, 20.5_

- [ ] 4. Phase 2 Checkpoint
  - Run `mvn test -Dtest=AuthServiceTest,JwtServiceTest,PasswordUtilTest,DateTimeUtilTest`; all pass. Start app against Docker MySQL; Flyway applies V1 and V2 cleanly; `AdminSeeder` creates default admin on first run, skips on second run. Login flow works end-to-end from the React frontend (login → token in memory → refresh on reload → logout clears cookie).

---

### Phase 3 — Admin Module

- [ ] 5. Build file storage, shared services, and Admin backend
  - [ ] 5.1 Implement `FileStorageService` and `LocalFileStorageService` (A7)
    - `FileStorageService` interface: `store(MultipartFile, subfolder) → String` (returns UUID filename); `load(subfolder, filename) → Resource`; `delete(subfolder, filename)`
    - `LocalFileStorageService` (`@ConditionalOnProperty(name="storage.type", havingValue="local", matchIfMissing=true)`): store files under `UPLOAD_DIR/{subfolder}/{uuid}.{ext}`; validate filename (no `..`, no `/`); use Apache Tika `tika-core` to detect MIME type from file content (not just extension) for all file-type validation
    - `GET /files/logo/{filename}` unauthenticated controller (outside `/api/v1/` prefix, A4): serve logo files publicly; validate filename; return file as `Resource` with correct `Content-Type`
    - File-type rules: profile photos and question images → JPEG, PNG, GIF (max 2 MB); logo → JPEG, PNG, WebP (max 2 MB, should-fix); CSV templates → CSV (max 5 MB)
    - _Requirements: 17.4, 21.5_

  - [ ] 5.2 Create remaining JPA entities for Admin module: `Student`, `ParentStudent`, `FacultyAssignment`, `AuditLog`, `SchoolSettings`, `Badge`, `StudentBadge`, `Notification`
    - All datetime fields as `java.time.Instant`
    - `SchoolSettings`: single-row table (`id=1`); fields: `schoolName`, `logoUrl`, `primaryColor`, `secondaryColor`, `leaderboardEnabled`, `emailNotificationsEnabled`, `updatedAtUtc`
    - `AuditLog`: fields from design; `oldValue` and `newValue` as `@Column(columnDefinition="JSON")`; no password fields
    - `Badge`, `StudentBadge`, `Notification` entities matching V1 schema
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 17.3, 21.6_

  - [ ] 5.3 Implement `UserService` with single-user CRUD and bulk upload
    - Create user: generate `Temporary_Password` via `PasswordUtil.generate()`, hash with `PasswordUtil.hash()`, set `mustChangePassword=true`; return plaintext password in response (ONE TIME ONLY); write `AuditLog` entry (action: USER_CREATED; `newValue` MUST NOT contain password or passwordHash)
    - Edit user (full_name, email, phone, role, class for students): write `AuditLog` entry
    - Deactivate user: set `isActive=false`; retain all historical data; write `AuditLog` entry; return HTTP 409 if trying to deactivate last active Admin
    - Reset password: generate new `Temporary_Password`, hash, set `mustChangePassword=true`; return plaintext once; write `AuditLog` entry (no password in log)
    - `bulkUpload(file, role)`: validate file ≤ 5 MB (HTTP 400 if exceeded), use Tika to validate CSV MIME type; validate header row (HTTP 400 if invalid, no rows processed); stream-read in chunks of 50; each chunk in its own `@Transactional`; per-row validation, create valid accounts, record failures; return HTTP 207 if partial success, HTTP 200 if all rows succeeded, HTTP 400 only for full-file rejection (should-fix)
    - Provide `GET /api/v1/admin/users/template` (CSV template download, Admin only)
    - `GET /api/v1/admin/users` with pagination (`page`, `size`), role filter, status filter, search by name/username (A14)
    - `PATCH /api/v1/admin/users/{id}/status` (activate/deactivate, A14)
    - `POST /api/v1/admin/users/bulk-upload`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12, 1.13, 21.6_

  - [ ] 5.4 Implement `AcademicService` and Admin academic-structure endpoints
    - CRUD for `AcademicYear` (exactly one can be `isCurrent=true`), `Class`, `Subject`
    - `FacultyAssignment` CRUD: enforce unique `(faculty_user_id, class_id, subject_id)`; write `AuditLog` entry on create and delete (A14)
    - `ParentStudent` CRUD: link/unlink parent-student; max 2 links per student; write `AuditLog` entry on create and delete
    - Class deletion: return HTTP 409 if enrolled students exist; require explicit confirmation flag in request body
    - Endpoints under `AdminController` with `@PreAuthorize("hasRole('ADMIN')")`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 21.6_

  - [ ] 5.5 Implement `SettingsService` and `AdminController` settings endpoints
    - `GET /api/v1/settings` (authenticated, A4): return `SchoolSettings` DTO (schoolName, logoUrl, primaryColor, secondaryColor, leaderboardEnabled, emailNotificationsEnabled)
    - `PUT /api/v1/admin/settings`: update school name, colors, toggles; write `AuditLog` entry (record old + new values for changed fields; never include file binary in log)
    - `POST /api/v1/admin/settings/logo`: accept logo upload; validate MIME type via Tika (JPEG, PNG, WebP only, max 2 MB, should-fix); store via `FileStorageService`; update `logoUrl`; write `AuditLog`
    - _Requirements: 15.2, 17.3, 17.4, 17.5, 17.6, 17.7_

- [ ] 6. Build shared UI component library and Admin frontend
  - [ ] 6.1 Build shared primitive UI components (A14)
    - Create `frontend/src/components/ui/` directory
    - Implement: `Button` (variants: primary, secondary, danger, ghost; size: sm/md/lg; loading state with `Spinner`), `Card` (rounded-2xl, soft shadow, bg-card), `Modal` (accessible — trap focus, `aria-modal`, `role="dialog"`, close on Escape), `Table` (sortable columns, responsive), `Pagination` (page/size controls), `Badge` (colored pill), `Spinner` (accessible loading indicator), `EmptyState` (icon + message), `ErrorState` (icon + message + retry button), `ConfirmDialog` (reusable confirmation modal with confirm/cancel actions), `Skeleton` (pulsing loading placeholder)
    - All interactive elements: minimum 44×44 px tap targets; WCAG AA contrast; visible keyboard focus indicators
    - _Requirements: 18.8, 18.9, 18.10, 18.11_

  - [ ] 6.2 Build shared form components (A14)
    - Create `frontend/src/components/form/` directory
    - Implement: `FormInput`, `FormSelect`, `FormTextarea`, `FormCheckbox`, `FileUpload` — all integrate with React Hook Form via `Controller` or `register`; all have associated accessible `<label>` elements; show inline error messages adjacent to invalid fields; `FileUpload` supports `accept` prop (e.g., `image/jpeg,image/png,image/webp` for logo; `image/jpeg,image/png,image/gif` for questions)
    - _Requirements: 18.11_

  - [ ] 6.3 Build `ThemeContext` and apply runtime branding overrides
    - `ThemeContext.jsx`: on app load (after authentication), call `GET /api/v1/settings` with access token; merge Admin overrides (`primaryColor`, `secondaryColor`, `schoolName`, `logoUrl`) on top of `defaultColors` from `theme.js`; inject merged CSS vars (both hex and RGB channel variants) into `document.documentElement.style`; expose `settings` and `refreshTheme()` via context
    - `TopBar.jsx`: display school name from `ThemeContext`; `NotificationBell` with unread count; profile menu with user's full name and logout button
    - `AuthLayout.jsx`: public wrapper (login, change-password pages); shows logo from settings (fetched via unauthenticated `/files/logo/{filename}` endpoint); no sidebar
    - _Requirements: 17.1, 17.2, 17.3, 18.5_

  - [ ] 6.4 Build Admin layout and navigation
    - `AdminLayout.jsx`: `ProtectedRoute role=ADMIN`; left `Sidebar` on desktop (≥1024 px) with icons + labels; hamburger `Drawer` on mobile (<1024 px); `TopBar`; `Breadcrumbs` on deep pages
    - `Sidebar.jsx`: nav links for all Admin pages; active-link highlight using `NavLink`
    - `Drawer.jsx`: slide-in mobile nav with overlay backdrop; close on outside click or Escape
    - `Breadcrumbs.jsx`: auto-generated from route path; back navigation control on all deep pages
    - _Requirements: 18.1, 18.3, 18.4, 18.5, 18.6_

  - [ ] 6.5 Build `UserManagementPage` and modals
    - `UserManagementPage.jsx`: filterable/searchable user table (role, status, name/username search); `CreateUserModal` (React Hook Form, all required fields, role-conditional class field for STUDENT); `TempPasswordModal` (one-time display with explicit dismiss button — never auto-close); `BulkUploadModal` (file picker, progress, results table with success/failure rows); `CSVTemplateDownload` buttons
    - Wire to `GET /api/v1/admin/users`, `POST /api/v1/admin/users`, `PATCH /api/v1/admin/users/{id}`, `PATCH /api/v1/admin/users/{id}/status`, `POST /api/v1/admin/users/bulk-upload`, `GET /api/v1/admin/users/template`
    - _Requirements: 1.1, 1.2, 1.3, 1.9, 1.10, 1.11, 1.12, 1.13_

  - [ ] 6.6 Build Admin academic-structure pages
    - `ClassManagementPage.jsx`: create/edit/delete classes (confirm dialog on delete with enrolled students)
    - `SubjectManagementPage.jsx`: create/edit/delete subjects
    - `FacultyAssignmentPage.jsx`: create/delete faculty assignments; show conflict on duplicate (faculty_user_id, class_id, subject_id)
    - `ParentLinksPage.jsx`: create/delete parent-student links; show student's name and parent's name
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8_

  - [ ] 6.7 Build `SchoolSettingsPage` and `AuditLogPage`
    - `SchoolSettingsPage.jsx`: school name text field; logo upload (`FileUpload` with WebP/PNG/JPEG accept, A14 should-fix); primary/secondary color pickers; leaderboard toggle; email notifications toggle; save button with confirmation
    - `AuditLogPage.jsx`: paginated table of audit entries; filter by action type and date range; columns: Admin name, action, entity type, entity id, timestamp (IST)
    - _Requirements: 17.3, 17.4, 17.5, 17.6, 17.7, 21.6_

  - [ ] 6.8 Write JUnit 5 unit tests for `UserService`, `AcademicService`, and `SettingsService`
    - `UserService`: test bulk-upload HTTP 207 partial success vs HTTP 200 all-success vs HTTP 400 file rejection; test temporary password not in audit log; test deactivate retains data; test duplicate username/email returns 409
    - `AcademicService`: test 409 on class delete with students; test unique constraint on faculty assignments
    - `SettingsService`: test logo MIME validation rejects GIF; accepts WebP
    - _Requirements: 1.7, 1.10, 1.12, 1.13, 3.8, 17.4_

- [ ] 7. Phase 3 Checkpoint
  - All Admin CRUD operations work end-to-end from the browser; `TempPasswordModal` dismissal hides the password permanently; bulk upload returns HTTP 207 on partial success; logo upload rejects GIF, accepts WebP; audit log records all Admin actions; shared UI component library renders correctly in all Admin pages; `ThemeContext` applies Admin color overrides without rebuild.

---

### Phase 4 — Faculty Module

- [ ] 8. Build question bank, quiz engine, and grading backend
  - [ ] 8.1 Create Faculty and Quiz-related JPA entities
    - `Question` entity: `id`, `createdBy` (FK→users), `subjectId` (FK), `classLevel`, `topic`, `type` (enum: MCQ, TRUE_FALSE, SHORT_ANSWER), `text`, `imageUrl`, `difficulty` (enum: EASY, MEDIUM, HARD), `marks`, `explanation`, `modelAnswer`, `isActive`, `createdAtUtc`, `updatedAtUtc`
    - `QuestionOption` entity: `id`, `questionId` (FK), `optionKey`, `optionText`, `isCorrect`, `sortOrder`
    - `Quiz` entity: all fields from design including `status` (enum: DRAFT, SCHEDULED, LIVE, CLOSED), `shuffleQuestions`, `shuffleOptions`, `showAnswers` (enum: NEVER, AFTER_SUBMIT, AFTER_END), `passPercentage`, `totalMarks`, `reminder24hSent`, `reminder1hSent`
    - `QuizClass` entity: `quizId`, `classId`
    - `QuizQuestion` entity: `quizId`, `questionId`, `sortOrder`, `marks`
    - `QuizQuestionSnapshot` entity: `quizId`, `questionId`, `snapshotText`, `snapshotOptions` (JSON), `correctOptionKey`, `marks`, `type`, `modelAnswer`, `explanation`, `imageUrl`, `createdAtUtc`
    - `QuizAttempt`, `AttemptAnswer` entities (moved to Phase 4, A7): `QuizAttempt` has `questionOrder` (JSON), `optionOrders` (JSON), `effectiveDeadlineUtc`, `isAutoSubmitted`; `AttemptAnswer` has `selectedOptionKey`, `textAnswer`, `isCorrect`, `marksAwarded`, `gradingStatus` (enum: PENDING, AUTO_GRADED, GRADED), `facultyFeedback`, `markedForReview`
    - `AttemptGradedEvent` and `BadgesAwardedEvent` Spring event classes (moved to Phase 4, A7): simple POJOs carrying `QuizAttempt` and student/badge references respectively
    - _Requirements: 4.1, 4.2, 5.1, 6.1, 8.1_

  - [ ] 8.2 Implement `QuestionService` and question bank endpoints
    - CRUD scoped to faculty's `faculty_assignments`: on create/edit, verify `(subjectId, classLevel)` is in faculty's assignments (HTTP 403 otherwise)
    - Soft-delete: `isActive=false` (never hard-delete)
    - Image upload: delegate to `FileStorageService`; validate via Tika (JPEG/PNG/GIF, max 2 MB)
    - Filter/search endpoint: `GET /api/v1/faculty/questions?subject=&classLevel=&topic=&type=&difficulty=&page=&size=`
    - Ownership + assignment check on edit: check BOTH that `question.createdBy == auth.userId` AND assignment still exists (should-fix)
    - `GET /api/v1/faculty/assignments` endpoint: returns current faculty member's list of `(classId, className, subjectId, subjectName)` assignments — used by frontend dropdowns (A14)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10_

  - [ ] 8.3 Implement `QuizService` with publish validation, snapshot creation, and quiz lifecycle (A8, A9, should-fix)
    - Quiz CRUD: enforce `(subjectId, classIds)` all within faculty's assignments on create; check quiz ownership (`quiz.createdBy == auth.userId`) AND assignment check on all mutations (should-fix)
    - Allow duplication from DRAFT, SCHEDULED, LIVE, or CLOSED status (should-fix — no status restriction)
    - `publish(quizId, facultyId)`:
      1. Assert `status=DRAFT`
      2. Validate 5 conditions (Req 5.4a–e); throw `QuizPublishValidationException` (HTTP 400) on any failure
      3. For each question: `INSERT IGNORE INTO quiz_question_snapshots ...` (A8 — not ON DUPLICATE KEY UPDATE)
      4. Set `quiz.status=SCHEDULED`
    - Edit while SCHEDULED + zero attempts: delete existing snapshots for quiz, then re-insert (A8); if attempts exist, snapshots are immutable
    - Edit while attempts exist: restrict to extending `end_time` only (HTTP 400 for other fields)
    - `manualClose(quizId)`: set `end_time=now_utc`, transition to CLOSED; auto-submit all IN_PROGRESS attempts for this quiz (conditional SQL update); if `pendingShortAnswerCount(quizId)==0` then call `badgeService.evaluateQuizChampion(quizId)` (A9)
    - `totalMarks`: computed as sum of `quiz_questions.marks` whenever questions change
    - `QuizStatusScheduler` (`@Scheduled(fixedDelay=60_000)`): SCHEDULED→LIVE when `start_time <= now`; LIVE→CLOSED when `end_time < now`; on CLOSED transition, if `pendingShortAnswerCount(quizId)==0` call `badgeService.evaluateQuizChampion(quizId)` (A9); use conditional SQL updates
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11_

  - [ ] 8.4 Implement `GradingService` with auto-grading, manual grading, and finalization (A10)
    - Auto-grade MCQ/TRUE_FALSE on submit: compare `selectedOptionKey` with `snapshot.correctOptionKey`; set `isCorrect`, `marksAwarded`, `gradingStatus=AUTO_GRADED`
    - Manual grading: `PUT /api/v1/faculty/grading/answers/{answerId}` — validate `marksAwarded` between 0 and `snapshot.marks`; update `facultyFeedback`; set `gradingStatus=GRADED`; after each grade, check if all answers in the attempt are graded → call `finalizeAttempt()`
    - `finalizeAttempt(attemptId)` annotated `@Transactional` (A10): compute `totalScore`, `percentage`, `timeTakenSeconds`; set `attempt.status=GRADED`; save; call `eventPublisher.publishEvent(new AttemptGradedEvent(attempt))` at end of method body (Spring holds event until AFTER_COMMIT per listener's `AFTER_COMMIT` phase)
    - Grading queue endpoint: `GET /api/v1/faculty/grading?quizId=` — returns PENDING short-answer responses grouped by quiz; each row shows student answer, model answer, max marks, attempt id
    - Restrict grading to faculty's assigned quizzes
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [ ] 8.5 Write JUnit 5 unit tests for `QuizService`, `QuestionService`, and `GradingService`
    - `QuizService.publish()`: test each of the 5 validation conditions individually; test INSERT IGNORE idempotency; test snapshot delete-and-regenerate when SCHEDULED with zero attempts
    - `GradingService.computeFinalScore()`: test MCQ-only quiz auto-grades to GRADED; test mixed quiz stays SUBMITTED until all SHORT_ANSWER graded; test marks sum
    - `QuestionService`: test 403 on out-of-assignment subject; test soft-delete preserves references
    - _Requirements: 4.4, 4.5, 4.6, 5.4, 8.5, 8.6_

  - [ ]* 8.6 Write jqwik property test for Property 2 (Snapshot Immutability)
    - `@Property(tries=100) @Tag("Feature: nexus-quiz-portal, Property 2: Snapshot Immutability")`
    - After creating a snapshot for a `(quiz_id, question_id)`, run any sequence of question-edit operations on the parent question; assert via direct repository query that `quiz_question_snapshots` row is unchanged
    - _Requirements: 4.8, 6.16_

- [ ] 9. Build Faculty frontend pages
  - [ ] 9.1 Build Faculty layout and navigation
    - `FacultyLayout.jsx`: left `Sidebar` (desktop ≥1024 px); hamburger `Drawer` (mobile); `TopBar`; `Breadcrumbs`
    - Nav links: Dashboard, Question Bank, My Quizzes, Grading, Student Performance
    - _Requirements: 18.1, 18.3, 18.4, 18.5_

  - [ ] 9.2 Build `QuestionBankPage` and `QuestionFormModal`
    - `QuestionBankPage.jsx`: filter bar (subject, class level, topic, type, difficulty); paginated question table; create/edit via `QuestionFormModal`; soft-delete with confirmation
    - `QuestionFormModal.jsx`: question type selector; dynamic option fields for MCQ (2–6 options, mark correct); image upload field; model answer field (SHORT_ANSWER only); subject/class dropdowns populated from `GET /api/v1/faculty/assignments`
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [ ] 9.3 Build `MyQuizzesPage`, `QuizFormPage`, and quiz action flows (A14)
    - `MyQuizzesPage.jsx`: paginated quiz list with status filter (DRAFT/SCHEDULED/LIVE/CLOSED); action menu per quiz: Edit, Publish (confirm), Close (confirm), Duplicate (A14, should-fix — all statuses)
    - `QuizFormPage.jsx` (create and edit): quiz metadata form (title, subject, class sections, duration, start/end datetime picker in IST, max_attempts, shuffle toggles, show_answers selector, pass_percentage); question picker panel (filter bank, add/remove questions, reorder); total_marks display (auto-computed); `PublishButton` triggers publish validation
    - When SCHEDULED with attempts: disable all fields except `endTime`
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9_

  - [ ] 9.4 Build `FacultyDashboard` (A14)
    - `FacultyDashboard.jsx`: assigned classes/subjects summary cards; upcoming quizzes list (next 7 days, sorted by start_time IST); pending grading count badge linking to `GradingQueuePage`
    - Endpoint: `GET /api/v1/faculty/dashboard` — returns assignments summary, upcoming quizzes, pending grading count
    - _Requirements: 13.1_

  - [ ] 9.5 Build `GradingQueuePage` and grading flow
    - `GradingQueuePage.jsx`: group pending SHORT_ANSWER responses by quiz; for each response: show student answer text, model answer, marks input (0 to max), optional feedback textarea; submit grade button per answer; auto-advance to next pending response after submit
    - After grading last answer in attempt, poll `GET /api/v1/student/attempts/{id}/result` (A10) to show finalized score
    - _Requirements: 8.1, 8.2, 8.3, 8.4_

  - [ ] 9.6 Build `QuizReportPage` and `StudentPerformancePage`
    - `QuizReportPage.jsx`: stats cards (avg score, highest, lowest, pass %); score distribution bar chart (Recharts); student score table with individual scores; non-attempt list; export buttons (CSV/PDF) calling `GET /api/v1/faculty/quizzes/{id}/report/export?format=csv|pdf`
    - `StudentPerformancePage.jsx`: class/subject filter; student trend table showing Best_Attempt percentages per quiz; question analysis table (% correct per question)
    - _Requirements: 13.2, 13.3, 13.4, 13.5, 13.6, 13.7, 13.8_

- [ ] 10. Phase 4 Checkpoint
  - Faculty can create questions, build a quiz, publish it, and it transitions SCHEDULED→LIVE→CLOSED via the scheduler; GradingService auto-grades MCQ attempts and finalizes them; AttemptGradedEvent fires after commit (confirmed by BadgeService/NotificationService stubs); snapshot INSERT IGNORE verified; SCHEDULED-with-zero-attempts edit regenerates snapshots; all JUnit 5 and jqwik tests pass.

---

### Phase 5 — Student Module

- [ ] 11. Build student attempt engine, auto-save, and student frontend
  - [ ] 11.1 Implement `AttemptService` — start, resume, and shuffle (A9)
    - `startAttempt(quizId, studentId)`:
      1. Check liveness by comparing `now_utc` directly against `quiz.start_time_utc` and `quiz.end_time_utc` (NOT stored `status` column, A9)
      2. Verify student belongs to a class assigned to the quiz
      3. Count existing attempts; if `attemptsUsed >= quiz.maxAttempts` → HTTP 403
      4. If an IN_PROGRESS attempt exists → return it (idempotent, HTTP 200)
      5. Create attempt: `startedAt=now_utc`; `effectiveDeadline=MIN(startedAt + duration*60s, quiz.endTime)` as `Instant`; generate shuffled question order (if `shuffleQuestions=true`) and option orders (if `shuffleOptions=true`) — store once in JSON; create `AttemptAnswer` rows for all questions (PENDING, nulls)
    - `getAttempt(attemptId, studentId)`: return saved state + `remainingSeconds=(effectiveDeadline - now_utc).getSeconds()` (never negative); strip correct-answer fields (Property 3)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [ ] 11.2 Implement `AttemptService` — save answer, submit, result, and visibility filter
    - `saveAnswer(attemptId, questionId, answer, studentId)`: assert ownership; assert `status=IN_PROGRESS`; check grace window (10 s after `effectiveDeadline`); upsert `attempt_answers` row (unique on `attempt_id, quiz_question_id`)
    - `manualSubmit(attemptId, studentId)`: idempotent (return current result if already SUBMITTED/GRADED); set `status=SUBMITTED`; handle late-grace deadline (`submitted_at=effectiveDeadline` if within grace); call `GradingService.autoGradeAndFinalize()`
    - `getResult(attemptId, studentId)` (lazy finalization, A10): if IN_PROGRESS and past deadline → auto-submit; if SUBMITTED with no pending short answers → finalize; apply `filterAnswerVisibility()`; return result
    - `filterAnswerVisibility(attempt, quiz, attemptsUsed)`: implement all 3 `show_answers` modes (NEVER, AFTER_SUBMIT, AFTER_END) with correct conditions from design algorithm (c)
    - `AutoSubmitScheduler` (`@Scheduled(fixedDelay=60_000)`): conditional SQL `UPDATE quiz_attempts SET status='SUBMITTED' WHERE status='IN_PROGRESS' AND effective_deadline_utc < :now AND id=:id`; for each row updated, call `autoGradeAndFinalize()`; catch per-attempt exceptions (A9)
    - _Requirements: 6.7, 6.8, 6.9, 6.10, 6.11, 6.15, 6.16, 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [ ] 11.3 Implement Student endpoints
    - `GET /api/v1/student/quizzes` — list LIVE quizzes for student's class (with start/end IST, duration, attempt count)
    - `GET /api/v1/student/quizzes/{id}` — single quiz detail (A14): title, subject, class, duration, start/end in IST, attempt count used vs max, quiz status; used by `QuizInstructionsPage`
    - `POST /api/v1/student/quizzes/{id}/start` — idempotent start
    - `GET /api/v1/student/attempts/{id}` — resume attempt (stripped response)
    - `PATCH /api/v1/student/attempts/{id}/answers` — save single answer
    - `POST /api/v1/student/attempts/{id}/submit` — manual submit
    - `GET /api/v1/student/attempts/{id}/result` — get result (lazy finalization)
    - `GET /api/v1/student/history` — all past attempts, paginated, filter by subject/date
    - All endpoints restricted to the authenticated student's own data (Property 8)
    - _Requirements: 6.1, 6.7, 6.11, 6.12, 9.1, 11.3, 11.6, 11.7_

  - [ ] 11.4 Write JUnit 5 unit tests for `AttemptService`
    - Test `computeEffectiveDeadline()`: cases where duration ends before quiz end, after quiz end, and exactly equal
    - Test idempotent start (returns existing IN_PROGRESS attempt)
    - Test grace window boundary (10 s): save accepted within grace, rejected after grace
    - Test idempotent submit (returns current result if already SUBMITTED)
    - Test `filterAnswerVisibility()` for all 3 `show_answers` modes including AFTER_END time boundary
    - _Requirements: 6.2, 6.7, 6.9, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 11.5 Write jqwik property test for Property 1 (Timer Invariant)
    - `@Property(tries=200) @Tag("Feature: nexus-quiz-portal, Property 1: Timer Invariant")`
    - For any generated `(startedAt, durationMinutes, endOffsetMinutes)` triple: `AttemptService.computeEffectiveDeadline(startedAt, durationMinutes, quizEnd)` equals `MIN(startedAt + duration*60s, quizEnd)` as `Instant`
    - Test edge cases: `startedAt + duration == quizEnd`, quiz ends before duration, deadline in the past
    - _Requirements: 6.2, 20.5_

  - [ ]* 11.6 Write jqwik property test for Property 3 (Answer Isolation)
    - `@Property(tries=100) @Tag("Feature: nexus-quiz-portal, Property 3: Answer Isolation")`
    - For any IN_PROGRESS attempt with varied question type combinations: serialize the full API response JSON to a string and assert that `"correct_option_key"`, `"is_correct":true`, and `"explanation"` do NOT appear
    - _Requirements: 6.6, 9.1, 23.6_

  - [ ]* 11.7 Write jqwik property test for Property 6 (Shuffle Stability)
    - `@Property(tries=100) @Tag("Feature: nexus-quiz-portal, Property 6: Shuffle Stability")`
    - For any `attempt_id` with shuffle enabled: call resume endpoint 10+ times; assert question order array is identical across all calls
    - _Requirements: 6.3, 6.4_

- [ ] 12. Build Student frontend pages
  - [ ] 12.1 Build Student layout and navigation
    - `StudentLayout.jsx`: left `Sidebar` (desktop ≥1024 px); `BottomNav` (mobile <1024 px); `TopBar`
    - `BottomNav.jsx`: bottom navigation bar with icons for Dashboard, Quizzes, History, Badges, Notifications
    - _Requirements: 18.1, 18.2, 18.4, 18.5_

  - [ ] 12.2 Build `QuizListPage` and `QuizInstructionsPage`
    - `QuizListPage.jsx`: list of available quizzes; `QuizCard` with subject, start/end IST, duration; "Start Quiz" button enabled ONLY when quiz status is LIVE
    - `QuizInstructionsPage.jsx`: quiz details from `GET /api/v1/student/quizzes/{id}` (A14); instructions text; "Start Attempt" button calls `POST /student/quizzes/{id}/start`
    - _Requirements: 11.3, 19.1_

  - [ ] 12.3 Build `QuizAttemptPage` with `useQuizAttempt` and `useAutoSave` hooks
    - `useQuizAttempt` hook: load attempt via `GET /api/v1/student/attempts/{id}`; manage question navigation state; poll `GET /api/v1/student/attempts/{id}` every 60 s to refresh `remainingSeconds` from server (A14 timer resync — replaces client countdown value on each poll to prevent drift)
    - `useAutoSave` hook: on answer change → debounce 800 ms → `PATCH /student/attempts/{id}/answers`; on navigate (Prev/Next/Palette) → cancel debounce → synchronous flush (await PATCH before rendering next question); retry on network error / HTTP 5xx: 1 s, 2 s, 4 s; stop retrying on 4xx; show `SaveIndicator` (idle/saving/saved/failed) and `SaveFailedBanner` after all retries exhausted
    - `QuizAttemptPage.jsx`: `QuizTimer` (Effective_Deadline countdown; amber at 5 min, red at 1 min); `QuestionDisplay`; `AnswerOption` (large tappable buttons); `QuizNavButtons` (Prev/Next); `MarkReviewToggle`; `QuestionPalette` (answered/unanswered/marked-for-review state per question); `ProgressBar`; `SaveIndicator`; `ConfirmDialog` on manual submit; distraction-free layout (no sidebar, minimal chrome)
    - _Requirements: 6.7, 6.8, 6.9, 6.11, 6.12, 6.13, 6.14, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7_

  - [ ] 12.4 Build `ResultPage` (A10)
    - `ResultPage.jsx`: after `POST /submit` returns, immediately call `GET /student/attempts/{id}/result` (A10) for the canonical result; display score summary (total score, percentage, pass/fail, time taken); per-question review (subject to `show_answers` visibility rules); `ConfettiOverlay` (canvas-confetti) triggered when Gold result or new badge earned — animation completes within 3 s and does not block interaction
    - "Partial result — pending review" banner when SHORT_ANSWER questions are ungraded
    - _Requirements: 8.7, 9.1, 9.2, 9.3, 9.4, 9.5, 11.7, 11.8, 19.8_

  - [ ] 12.5 Build `QuizHistoryPage` and `BadgesPage`
    - `QuizHistoryPage.jsx`: paginated list of all past attempts; filter bar (subject, date range); each entry links to `ResultPage`
    - `BadgesPage.jsx`: `BadgeShelf` — earned badges shown in color with awarded_at in IST; unearned badges shown as greyed-out icons with name visible
    - _Requirements: 10.12, 10.13, 10.15, 11.5, 11.6_

  - [ ] 12.6 Implement `DevDataSeeder` `@Profile("dev")` `ApplicationRunner` (A2)
    - `@Component @Profile("dev")` implementing `ApplicationRunner`
    - Seeds all data using JPA repositories from Phases 2–5 with `Instant.now()` relative times: one quiz starts in 1 hour, one is live now, two are closed; 1 Admin, 3 Faculty, 2 Classes (Class 6-A, Class 7-A), 5 Subjects, 10 Students, 6 Parents (some linked to 2 students), 30 Questions (MCQ/TF/SHORT_ANSWER, all difficulty levels), 4 Quizzes with attempts and results
    - Idempotent: check if seed data already exists (e.g., count Students > 0) and skip entirely if so
    - Remove `spring.flyway.locations` dev-profile override from `application-dev.yml` (A2)
    - _Requirements: 22.1, 22.2, 22.3_

  - [ ]* 11.8 Write jqwik property test for Property 4 (Best Attempt Consistency)
    - `@Property(tries=200) @Tag("Feature: nexus-quiz-portal, Property 4: Best Attempt Consistency")`
    - For any generated list of attempt percentages (including duplicates, single entries, monotonic sequences): `getBestAttemptPercentage(studentId, quizId)` returns `MAX(percentages)`
    - _Requirements: 6.15, 10.11, 11.2, 12.3_

- [ ] 13. Phase 5 Checkpoint
  - Full quiz attempt lifecycle works end-to-end: Student starts quiz → auto-save with debounce and flush-on-nav → manual submit → ResultPage shows score; AutoSubmitScheduler submits expired attempts; `filterAnswerVisibility()` correctly strips/shows answers per show_answers mode; `DevDataSeeder` seeds data in dev profile, skips on second run; all jqwik property tests (P1, P3, P4, P6) and JUnit 5 tests pass.

---

### Phase 6 — Parent Module

- [ ] 14. Build Parent backend and frontend
  - [ ] 14.1 Implement `ParentController` and parent-scoped data endpoints
    - All endpoints under `@PreAuthorize("hasRole('PARENT')")`
    - Before returning any student data, verify `student_id` is in `parent_student` for `auth.userId`; return HTTP 403 otherwise (Property 8)
    - `GET /api/v1/parent/children` — list linked students with name, class, roll number
    - `GET /api/v1/parent/children/{studentId}/dashboard` — upcoming quizzes, attempt history, avg score, subject-wise performance (Best_Attempt), badge shelf, class rank (if leaderboard enabled)
    - `GET /api/v1/parent/children/{studentId}/quizzes` — quiz list for child
    - `GET /api/v1/parent/children/{studentId}/attempts/{id}/result` — attempt result (subject to show_answers rules)
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [ ] 14.2 Build Parent layout and frontend pages
    - `ParentLayout.jsx`: left `Sidebar` (desktop); `BottomNav` (mobile); `TopBar`
    - `ParentDashboard.jsx`: `ChildSelector` (dropdown if >1 linked child); display child's upcoming quizzes, attempt history, avg score, subject-wise bar chart, trend line chart, badge shelf, class rank (if leaderboard enabled) — all from `GET /api/v1/parent/children/{id}/dashboard`
    - `ChildPerformancePage.jsx`: full child performance view (same components as dashboard, deeper detail)
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_

  - [ ] 14.3 Write JUnit 5 unit test for parent data boundary (Property 8)
    - Test that `GET /api/v1/parent/children/{studentId}/dashboard` returns HTTP 403 when the parent is NOT linked to the requested student
    - Test that only attempt rows with `student_id=auth.studentId` appear in student endpoints
    - _Requirements: 2.15, 12.5_

- [ ] 15. Phase 6 Checkpoint
  - Parent can view linked child's data; attempting to access an unlinked child's data returns HTTP 403; child selector updates all displayed data; leaderboard rank visible when leaderboard is enabled.

---

### Phase 7 — Dashboards and Charts

- [ ] 16. Build Analytics, Report services, and dashboard pages
  - [ ] 16.1 Implement `AnalyticsService` with Best_Attempt aggregation and leaderboard name derivation (A13)
    - All aggregation queries use Best_Attempt (highest `percentage` per `(student_id, quiz_id)` among GRADED attempts) — never raw attempts
    - Student dashboard: avg score, subject-wise performance, score trend, upcoming quizzes
    - Faculty analytics: quiz-wise stats (avg, highest, lowest, pass %), student score trends, question-wise correct-answer rate
    - Admin analytics: school overview (active students, faculty, monthly quizzes, overall avg), class-wise progress (avg per class/subject, top 5 / bottom 5), faculty activity (quizzes created/scheduled/conducted, avg score, participation rate, pending grading count)
    - Leaderboard DTO name derivation (A13): split `fullName` on the last space → `firstName` = everything before last space; `lastInitial` = first character of last word + "."; if no space → use full name as-is; derivation in `AnalyticsService` mapper, never stored separately
    - Leaderboard: per-class, per-quiz; ranks by Best_Attempt percentage descending; ties share rank; return HTTP 403 if student requests other class leaderboard (Req 16.4)
    - _Requirements: 11.2, 12.3, 13.2, 13.3, 13.4, 13.5, 14.1, 14.2, 14.3, 14.4, 14.5, 16.1, 16.3, 16.4_

  - [ ] 16.2 Implement `ReportService` with CSV and PDF export
    - CSV via OpenCSV: standard comma-separated; include header row; paginate for large datasets
    - PDF via OpenPDF: printable layout; school name header from `SchoolSettings`
    - `GET /api/v1/faculty/quizzes/{id}/report/export?format=csv|pdf` (Faculty only, restricted to assigned quizzes)
    - `GET /api/v1/admin/reports/export?type=students|faculty|class&format=csv|pdf` (Admin only, A14)
    - Restrict Faculty report exports to their faculty_assignments scope (HTTP 403 otherwise)
    - _Requirements: 13.6, 13.7, 13.8, 14.6_

  - [ ] 16.3 Build Recharts wrapper components and Student dashboard
    - `frontend/src/components/charts/`: `LineChart.jsx`, `BarChart.jsx`, `PieChart.jsx` — thin Recharts wrappers with consistent styling using CSS variable colors; accessible (aria-labels, title elements)
    - `StudentDashboard.jsx`: greeting card (student name + class); most recently earned badge; `UpcomingQuizCards`; `RecentResults`; avg score and total attempts count; `SubjectBarChart` (Best_Attempt % per subject); `TrendLineChart` (score over time)
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5_

  - [ ] 16.4 Build Faculty and Admin dashboard/analytics pages
    - `AdminDashboard.jsx`: overview cards (active students, active faculty, monthly quiz count, overall avg score); `ClassProgressTable`; `FacultyActivityTable`
    - `ClassProgressPage.jsx`: class/subject filter; progress table; top 5 / bottom 5 students
    - `StudentProfilePage.jsx` (Admin): all attempts, subject chart, trend line, badge shelf
    - `FacultyProgressPage.jsx`: faculty stats table
    - `LeaderboardPage.jsx` (Student): per-quiz leaderboard table (rank, name_initial, score); hidden if leaderboard disabled
    - `AdminReportsPage.jsx`: export form with type/format selectors and download button (A14)
    - _Requirements: 13.1, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 16.1, 16.2_

  - [ ]* 16.5 Write jqwik property test for Property 7 (Leaderboard Name Privacy)
    - `@Property(tries=200) @Tag("Feature: nexus-quiz-portal, Property 7: Leaderboard Name Privacy")`
    - For any generated `fullName`: if contains space → display = `firstName + " " + lastInitial + "."`; if no space → display = `fullName`; assert `lastName` field absent from leaderboard response JSON
    - Test with single-char last names, multi-word first names, names with special characters
    - _Requirements: 16.1_

- [ ] 17. Phase 7 Checkpoint
  - All dashboards show correct aggregated data using Best_Attempt; leaderboard shows first-name + last-initial only and is hidden when disabled; CSV and PDF exports download correctly; Admin reports export endpoint works for all types and formats; Recharts charts render with branding colors.

---

### Phase 8 — Notifications, Badge Events, and Email

- [ ] 18. Implement badge evaluation, notification delivery, and reminder scheduler
  - [ ] 18.1 Implement `BadgeService` with AFTER_COMMIT event listener and idempotent award (A9, should-fix)
    - `@TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT) @Transactional(propagation=REQUIRES_NEW)`
    - Evaluate on `AttemptGradedEvent`: Score badges (Gold/Silver/Bronze) using Best_Attempt percentage for the quiz; First Step (first GRADED attempt ever for student); Perfect Score (100%); Consistent Learner (5 consecutive best attempts across any subject with ≥60%); Rising Star (3 consecutive best attempts in same subject with strictly increasing percentages); Quiz Champion (see below)
    - Before each `INSERT student_badges`: check if row with same `(student_id, badge_id, quiz_id)` already exists; skip silently if so (idempotent, should-fix)
    - `evaluateQuizChampion(quizId)`: called by `QuizService.manualClose()`, `QuizStatusScheduler`, and `GradingService.finalizeAttempt()` when `pendingShortAnswerCount(quizId)==0 AND quiz.status=CLOSED` (A9); identify max Best_Attempt percentage across all students; award Quiz Champion to all students sharing that max; idempotent
    - Publish `BadgesAwardedEvent` after awarding badges
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 10.10, 10.11, 10.12_

  - [ ] 18.2 Implement `NotificationService` with AFTER_COMMIT listeners and email support
    - `@TransactionalEventListener(phase=AFTER_COMMIT) @Transactional(propagation=REQUIRES_NEW)` for `AttemptGradedEvent` and `BadgesAwardedEvent` listeners
    - Wrap listener body in `try/catch` — log failures, DO NOT propagate (Req 15.7, should not block grading)
    - Triggers (create `notifications` rows): new quiz scheduled (students + parents of targeted classes), result GRADED (student + parents), new badge earned (student)
    - Single `CommonController` with `PATCH /api/v1/notifications/{id}/read` and `PATCH /api/v1/notifications/read-all` — accessible to all authenticated roles (should-fix — not duplicated in role-specific controllers)
    - `GET /api/v1/notifications?page=&size=` — all roles
    - Email: when `emailNotificationsEnabled=true`, send via Jakarta Mail for each trigger; feature-flag gated — if disabled, skip email, still create in-app notification
    - _Requirements: 15.1, 15.3, 15.4, 15.5, 15.6, 15.7_

  - [ ] 18.3 Implement `ReminderScheduler` with conditional flag update (from design algorithm k)
    - `@Scheduled(fixedDelay=60_000)`: query quizzes with `reminder_24h_sent=false` AND `start_time BETWEEN now+23h AND now+25h`; conditional SQL `UPDATE quizzes SET reminder_24h_sent=true WHERE id=:id AND reminder_24h_sent=false`; only dispatch if `rows==1`; same for 1-hour window (`reminder_1h_sent`)
    - Send notifications to students of targeted classes AND their linked parents
    - All scheduling in UTC; IST only in notification message text (Req 15.2)
    - _Requirements: 15.1, 15.2, 20.4_

  - [ ] 18.4 Build notification frontend components
    - `NotificationBell.jsx`: bell icon in `TopBar`; unread count badge (from `GET /api/v1/notifications?unreadOnly=true&size=1` or a dedicated count endpoint)
    - `NotificationPanel.jsx`: dropdown panel showing latest notifications with title, message, timestamp in IST, read/unread indicator; "Mark all as read" button
    - `NotificationsPage.jsx` (all four role layouts): full notification list (paginated); mark individual or all as read
    - `ConfettiOverlay.jsx`: canvas-confetti animation; triggered on Gold result or new badge; completes ≤3 s; does not block interaction
    - _Requirements: 10.13, 15.3, 15.4_

  - [ ] 18.5 Write JUnit 5 unit tests for `BadgeService` and `NotificationService`
    - `BadgeService`: test each of the 8 badge award conditions; test idempotent award (no duplicate row on second evaluation); test Quiz Champion ties (both students awarded); test badge only awarded on GRADED (not SUBMITTED)
    - `NotificationService`: test exception in listener does NOT roll back grading transaction; test email is skipped when `emailNotificationsEnabled=false`
    - _Requirements: 10.1, 10.9, 10.10, 15.7_

  - [ ]* 18.6 Write jqwik property test for Property 5 (Badge GRADED-Only Invariant)
    - `@Property(tries=100) @Tag("Feature: nexus-quiz-portal, Property 5: Badge GRADED-Only Invariant")`
    - After any badge evaluation sequence: query `SELECT COUNT(*) FROM student_badges sb JOIN quiz_attempts qa ON sb.quiz_attempt_id = qa.id WHERE qa.status != 'GRADED'` and assert equals 0
    - _Requirements: 10.1_

- [ ] 19. Phase 8 Checkpoint
  - All 8 badge types award correctly; Quiz Champion evaluates after quiz closes; badge awards are idempotent (no duplicates); notification listener failure does not break grading transaction; reminder notifications fire once for each quiz (24h and 1h windows); confetti overlay plays on Gold result or new badge; email sends when enabled and skips when disabled.

---

### Phase 9 — Polish and Testing

- [ ] 20. Shared profile endpoints, integration tests, and out-of-scope verification
  - [ ] 20.1 Build profile page and endpoints for all four roles (A14)
    - `GET /api/v1/profile` and `PUT /api/v1/profile`: update `fullName`, `phone`; profile photo upload (JPEG/PNG/GIF, max 2 MB); return updated user DTO; accessible by all authenticated roles
    - `ProfilePage.jsx` shared component: profile info form (React Hook Form); photo upload with `FileUpload` component; "Change Password" section linking to `POST /api/v1/auth/change-password`; used by all four role layouts (StudentLayout, ParentLayout, FacultyLayout, AdminLayout)
    - _Requirements: 18.5_

  - [ ] 20.2 Accessibility and responsive design pass
    - Verify WCAG AA color contrast ratios for all branding colors (primary, secondary, success, error, warning) against bg/card backgrounds
    - Add visible keyboard focus indicators (`:focus-visible` outline) to all interactive elements
    - Add `alt` text to all images (badge icons, logo, question images, profile photos)
    - Ensure all form inputs have associated `<label>` elements; all dynamic elements (timer, palette, badge modals) have appropriate `aria-*` attributes
    - Verify minimum 44×44 px tap targets on Student and Parent interfaces
    - Verify layout is usable at 360 px minimum width (test on mobile viewport)
    - Google Fonts: add `<link>` for Nunito and Poppins in `index.html`
    - _Requirements: 18.6, 18.7, 18.8, 18.11_

  - [ ] 20.3 Write Spring Boot integration tests (Testcontainers MySQL) for critical flows
    - Full auth flow: login → access protected endpoint → token expiry → refresh → logout → refresh rejected (HTTP 401)
    - Quiz attempt lifecycle: student start → save answers → manual submit → auto-grade → GRADED result → badge awarded
    - Faculty grading flow: submit SHORT_ANSWER attempt → faculty grades → `AttemptGradedEvent` fires → attempt GRADED → notification created
    - Rate limiting: 20 POST /auth/login requests from same IP → 21st returns HTTP 429
    - File path traversal: request with `..` in filename → HTTP 400
    - Use `@SpringBootTest` + `@Testcontainers`; MySQL image from `testcontainers-mysql` BOM `1.19.8`
    - _Requirements: 2.13, 6.1, 6.9, 6.11, 8.5, 8.6, 21.3_

  - [ ]* 20.4 Write jqwik property test for Property 8 (Role Data Boundary)
    - `@Property(tries=100) @Tag("Feature: nexus-quiz-portal, Property 8: Role Data Boundary")`
    - For any generated set of students and parent-student links: querying `/parent/children/{studentId}` with a parent NOT linked to `studentId` returns HTTP 403
    - For student endpoints: all returned attempt rows have `student_id = auth.studentId`
    - _Requirements: 2.15, 12.5_

  - [ ] 20.5 Swagger UI and API documentation
    - Confirm Springdoc OpenAPI is configured: `springdoc.api-docs.path=/api-docs`, `springdoc.swagger-ui.path=/swagger-ui.html`
    - Add `@Operation`, `@ApiResponse`, `@SecurityRequirement` annotations to all Controller methods
    - Add `@SecurityScheme` for Bearer JWT in `SecurityConfig` or a dedicated OpenAPI config class
    - Verify Swagger UI accessible at `/swagger-ui.html` and shows all endpoints with request/response schemas
    - _Requirements: 23.4_

  - [ ] 20.6 Verify out-of-scope features are absent (Requirement 24)
    - Code review checklist: confirm no payment/fees endpoints exist; no WebSocket, video, audio, or real-time chat routes; no AI question generation or automated SHORT_ANSWER grading endpoints; no camera/screen-recording/tab-switch detection code; no attendance/timetable/homework/report-card modules; no native mobile app entry points; no SMS/WhatsApp sending code; no multi-tenant/school_id discriminator columns or logic; no i18n/l10n framework or translation files; no fill-in-the-blank or match-the-following question types
    - _Requirements: 24.1, 24.2, 24.3, 24.4, 24.5, 24.6, 24.7, 24.8, 24.9, 24.10_

  - [ ] 20.7 Performance, concurrency, and security smoke tests
    - Database index verification: run `SHOW INDEX FROM quiz_attempts`, `quizzes`, `notifications`, `students` and confirm all design-specified indexes exist
    - Concurrent auto-save test: simulate 10 concurrent `PATCH /attempts/{id}/answers` requests for the same attempt; confirm no duplicate rows (unique constraint on `attempt_id, quiz_question_id`) and all return HTTP 200
    - Stateless architecture: start two backend instances; login with one, use refresh token with the other — confirm it works (no shared session state)
    - Verify `spring.jpa.open-in-view=false` in all profiles (already set in 1.3)
    - _Requirements: 21.7, 21.8, 21.9, 21.10, 21.11_

- [ ] 21. Final Phase 9 Checkpoint — Ensure all tests pass
  - Run `mvn test` (all unit + jqwik property tests pass); run integration test suite with Testcontainers; start full stack (Docker MySQL + Spring Boot + Vite dev server); verify all four role flows end-to-end in browser; confirm out-of-scope checklist is clean; Swagger UI loads and documents all endpoints; accessibility pass complete; no stack traces in API responses in prod profile. Ask the user if any questions arise.

---

## Notes

- Tasks marked with `*` are optional property-based tests using jqwik `@Property` + `@ForAll` annotations; they can be skipped for a faster MVP but are highly recommended for correctness guarantees
- Regular JUnit 5 unit/integration tests do NOT have the `*` marker
- Each task references specific requirement clauses for traceability
- All amendments A1–A14 are incorporated: A1 (AdminSeeder ApplicationRunner), A2 (DevDataSeeder @Profile("dev")), A3 (all 8 badges in V2, badge SVG assets), A4 (SecurityConfig with @EnableMethodSecurity, JSON error handlers), A5 (refresh cookie path and profile-configurable Secure flag), A6 (per-IP + per-username rate limiting with X-Forwarded-For), A7 (attempt entities and events in Phase 4, FileStorageService before SettingsService), A8 (INSERT IGNORE, SCHEDULED-with-no-attempts regeneration), A9 (liveness by time window, Quiz Champion on scheduler close), A10 (ResultPage calls GET result, @Transactional finalizeAttempt), A11 (pinned versions, UTC JVM config), A12 (no `*` on non-jqwik tests), A13 (full_name split for leaderboard), A14 (all missing tasks added)
- Checkpoints ensure incremental validation at the end of each phase
- The DevDataSeeder (Phase 5) replaces Flyway dev-seed SQL entirely; seeds using `Instant.now()` relative times for live demo data

---

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3"] },
    { "id": 1, "tasks": ["1.4", "1.5", "1.6"] },
    { "id": 2, "tasks": ["1.7", "1.8"] },
    { "id": 3, "tasks": ["3.1", "3.2"] },
    { "id": 4, "tasks": ["3.3", "3.4", "3.5", "3.6"] },
    { "id": 5, "tasks": ["3.7", "3.8"] },
    { "id": 6, "tasks": ["3.9", "3.10"] },
    { "id": 7, "tasks": ["3.11", "3.12"] },
    { "id": 8, "tasks": ["5.1", "5.2"] },
    { "id": 9, "tasks": ["5.3", "5.4"] },
    { "id": 10, "tasks": ["5.5", "6.1", "6.2"] },
    { "id": 11, "tasks": ["6.3", "6.4"] },
    { "id": 12, "tasks": ["6.5", "6.6", "6.7"] },
    { "id": 13, "tasks": ["6.8", "8.1"] },
    { "id": 14, "tasks": ["8.2", "8.3"] },
    { "id": 15, "tasks": ["8.4", "9.1"] },
    { "id": 16, "tasks": ["8.5", "8.6", "9.2", "9.3", "9.4"] },
    { "id": 17, "tasks": ["9.5", "9.6"] },
    { "id": 18, "tasks": ["11.1"] },
    { "id": 19, "tasks": ["11.2", "11.3"] },
    { "id": 20, "tasks": ["11.4", "11.5", "11.6", "11.7", "12.1"] },
    { "id": 21, "tasks": ["12.2"] },
    { "id": 22, "tasks": ["12.3"] },
    { "id": 23, "tasks": ["12.4", "12.5", "12.6", "11.8"] },
    { "id": 24, "tasks": ["14.1"] },
    { "id": 25, "tasks": ["14.2", "14.3"] },
    { "id": 26, "tasks": ["16.1"] },
    { "id": 27, "tasks": ["16.2", "16.3"] },
    { "id": 28, "tasks": ["16.4", "16.5"] },
    { "id": 29, "tasks": ["18.1"] },
    { "id": 30, "tasks": ["18.2", "18.3"] },
    { "id": 31, "tasks": ["18.4", "18.5"] },
    { "id": 32, "tasks": ["18.6"] },
    { "id": 33, "tasks": ["20.1", "20.2", "20.5", "20.6"] },
    { "id": 34, "tasks": ["20.3"] },
    { "id": 35, "tasks": ["20.4", "20.7"] }
  ]
}
```
