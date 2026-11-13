-- Deploy actions now record the environment and the version in their own columns, so the dashboard can tell who
-- deployed what is running in an environment. Other actions, and deploys recorded before this, leave them null.
ALTER TABLE audit_log ADD COLUMN environment VARCHAR(50);
ALTER TABLE audit_log ADD COLUMN version VARCHAR(50);
