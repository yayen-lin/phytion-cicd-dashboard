-- Actions now name their target by app id ("sample-app") instead of the pipeline id ("jenkins:sample-app") or the
-- release app id ("registry:sample-app"). Every app so far had the same name for its app id, Jenkins job and image.
UPDATE audit_log SET target = SUBSTRING(target, LOCATE(':', target) + 1)
WHERE target LIKE 'jenkins:%' OR target LIKE 'registry:%';
