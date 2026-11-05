package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit log API. Read-only: entries are recorded by the services that perform the actions.
 */
@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * The most recent entries, newest first. Each parameter left out matches every entry.
     *
     * @param target       e.g. "jenkins:sample-app"
     * @param actionType   e.g. TRIGGER_BUILD
     * @param username     who took the action
     * @param actionResult SUCCESS or FAILED
     */
    @GetMapping("/api/audit")
    public List<AuditEntry> recentEntries(
            @RequestParam(required = false) String target,
            @RequestParam(required = false) AuditEntry.ActionType actionType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditEntry.ActionResult actionResult) {
        return auditService.getRecentEntries(new AuditFilter(target, actionType, username, actionResult));
    }

    /** The targets, action types and users that appear in the log, for the filter dropdowns */
    @GetMapping("/api/audit/filters")
    public AuditService.AuditFilterOptions filterOptions() {
        return auditService.getFilterOptions();
    }
}
