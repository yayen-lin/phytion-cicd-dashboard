-- Build actions now name their target by pipeline id ("jenkins:sample-app") instead of the bare job name.
-- Every earlier build action was on Jenkins.
UPDATE audit_log SET target = 'jenkins:' || target WHERE action_type = 'TRIGGER_BUILD' AND target NOT LIKE '%:%';
