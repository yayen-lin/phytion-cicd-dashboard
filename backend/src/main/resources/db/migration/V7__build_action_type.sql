-- The build action is now called BUILD, like RELEASE and DEPLOY, instead of TRIGGER_BUILD.
UPDATE audit_log SET action_type = 'BUILD' WHERE action_type = 'TRIGGER_BUILD';
