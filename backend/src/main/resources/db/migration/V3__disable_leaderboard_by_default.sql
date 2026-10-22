ALTER TABLE school_settings
    ALTER COLUMN leaderboard_enabled SET DEFAULT 0;

UPDATE school_settings
SET leaderboard_enabled = 0
WHERE id = 1
  AND NOT EXISTS (
      SELECT 1
      FROM audit_log
      WHERE target_entity_type = 'SCHOOL_SETTINGS'
        AND action_type = 'SCHOOL_SETTINGS_UPDATED'
  );
