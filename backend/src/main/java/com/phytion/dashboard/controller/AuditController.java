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
     * Get most recent entries, newest first.
     *
     * @param target       the app
     * @param actionType   an action taken on the dashboard
     * @param username     who took the action
     * @param actionResult SUCCESS or FAILED
     */
    // UNUSED: no frontend view calls this since 5.7 (the History page uses /api/history); kept for review
    @GetMapping("/api/audit")
    public List<AuditEntry> recentEntries(
            @RequestParam(required = false) String target,
            @RequestParam(required = false) AuditEntry.ActionType actionType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) AuditEntry.ActionResult actionResult) {
        return auditService.getRecentEntries(
                new AuditFilter(target, actionType, username, actionResult, null, null, null));
    }
}
