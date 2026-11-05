package com.phytion.dashboard.model;

/**
 * Which audit log entries to show. A null field matches every entry.
 *
 * @param target       e.g. "jenkins:sample-app"
 * @param actionType   e.g. TRIGGER_BUILD
 * @param username     who took the action
 * @param actionResult whether the action worked
 */
public record AuditFilter(
        String target,
        AuditEntry.ActionType actionType,
        String username,
        AuditEntry.ActionResult actionResult) {
}
