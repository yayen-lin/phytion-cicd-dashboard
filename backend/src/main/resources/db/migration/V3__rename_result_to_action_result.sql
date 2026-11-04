-- "result" -> "action_result": it is whether the action worked, not whether the build passed
ALTER TABLE audit_log ALTER COLUMN result RENAME TO action_result;
