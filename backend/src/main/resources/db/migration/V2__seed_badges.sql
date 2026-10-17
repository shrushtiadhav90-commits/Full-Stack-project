-- =============================================================================
-- V2__seed_badges.sql
-- Seed all 8 default badges for the Nexus Quiz Portal.
-- Uses INSERT IGNORE so this migration is idempotent – re-running it never
-- creates duplicate rows.
-- =============================================================================

INSERT IGNORE INTO badges (code, name, description, icon, type, min_percentage, is_active)
VALUES
    -- ── Score badges ──────────────────────────────────────────────────────────
    (
        'GOLD',
        'Gold',
        'Scored 90% or above on a quiz. Outstanding performance!',
        'gold.svg',
        'SCORE',
        90.00,
        1
    ),
    (
        'SILVER',
        'Silver',
        'Scored between 75% and 89% on a quiz. Excellent work!',
        'silver.svg',
        'SCORE',
        75.00,
        1
    ),
    (
        'BRONZE',
        'Bronze',
        'Scored between 60% and 74% on a quiz. Good effort!',
        'bronze.svg',
        'SCORE',
        60.00,
        1
    ),

    -- ── Special badges ────────────────────────────────────────────────────────
    (
        'FIRST_STEP',
        'First Step',
        'Completed your very first quiz. Welcome to the journey!',
        'first-step.svg',
        'SPECIAL',
        0.00,
        1
    ),
    (
        'PERFECT_SCORE',
        'Perfect Score',
        'Scored 100% on a quiz. Absolutely perfect!',
        'perfect-score.svg',
        'SPECIAL',
        100.00,
        1
    ),
    (
        'CONSISTENT_LEARNER',
        'Consistent Learner',
        'Completed 5 quizzes in a row with 60% or above. Keep it up!',
        'consistent-learner.svg',
        'SPECIAL',
        60.00,
        1
    ),
    (
        'RISING_STAR',
        'Rising Star',
        'Improved your score in 3 consecutive quizzes of the same subject. You''re on the rise!',
        'rising-star.svg',
        'SPECIAL',
        0.00,
        1
    ),
    (
        'QUIZ_CHAMPION',
        'Quiz Champion',
        'Achieved the highest score in the class for a quiz. Champion!',
        'quiz-champion.svg',
        'SPECIAL',
        0.00,
        1
    );
