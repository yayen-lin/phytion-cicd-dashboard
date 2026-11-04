-- Naming cleanup: the action history is now called the audit log, and a CI "run" is now a "build"
ALTER TABLE action_log RENAME TO audit_log;
ALTER TABLE audit_log ALTER COLUMN run_id RENAME TO build_id;
