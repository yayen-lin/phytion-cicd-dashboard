package com.phytion.dashboard.model;

/**
 * Which audit log entries to show. A null field matches every entry.
 *
 * @param target       the app
 * @param actionType   an action taken on the dashboard
 * @param username     who took the action
 * @param actionResult whether the action worked
 * @param buildId      the CI build the action started
 * @param environment  where it was deployed
 * @param version      the version released or deployed
 */
public record AuditFilter(
        String target,
        AuditEntry.ActionType actionType,
        String username,
        AuditEntry.ActionResult actionResult,
        String buildId,
        String environment,
        String version) {
}
