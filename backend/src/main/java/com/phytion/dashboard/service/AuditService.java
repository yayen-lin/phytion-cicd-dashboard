package com.phytion.dashboard.service;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * The audit log records every action taken through the dashboard.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** The size of the details column */
    private static final int MAX_DETAILS_LENGTH = 1000;

    private final AuditEntryRepository entries;

    @Value("${dashboard.audit.max-entries}")
    private final int maxEntries;

    /**
     * Adds an entry with a timestamp.
     */
    public AuditEntry add(String username, AuditEntry.ActionType actionType, String target,
                          AuditEntry.ActionResult actionResult, String buildId, String details) {
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH);
        }
        return entries.save(new AuditEntry(username, actionType, target, Instant.now(), actionResult, buildId, details));
    }

    /** Get most recent entries, newest first. */
    public List<AuditEntry> getRecentEntries() {
        return entries.findByOrderByIdDesc(Limit.of(maxEntries));
    }

    /** Get most recent entries about one target (e.g. the pipeline "jenkins:sample-app"), newest first. */
    public List<AuditEntry> getRecentEntries(String target) {
        return entries.findByTargetOrderByIdDesc(target, Limit.of(maxEntries));
    }
}
