package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.AuditEntry;
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

    /** @param target only entries about this target, e.g. "jenkins:sample-app"; all entries when left out */
    @GetMapping("/api/audit")
    public List<AuditEntry> recentEntries(@RequestParam(required = false) String target) {
        return target == null ? auditService.getRecentEntries() : auditService.getRecentEntries(target);
    }
}
