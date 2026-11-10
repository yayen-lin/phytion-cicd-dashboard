package com.phytion.dashboard.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.repository.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * The audit log records every action taken on the dashboard.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository repo;

    @Value("${dashboard.audit.max-entries}")
    private final int maxEntries;

    @Value("${dashboard.audit.details-max-length}")
    private final int detailsMaxLength; // must not exceed the VARCHAR(1000)

    /**
     * Adds an entry with a timestamp.
     */
    public AuditEntry add(String username, AuditEntry.ActionType actionType, String target,
                          AuditEntry.ActionResult actionResult, String buildId, String details) {
        if (details != null && details.length() > detailsMaxLength) {
            details = details.substring(0, detailsMaxLength);
        }
        return repo.save(new AuditEntry(username, actionType, target, Instant.now(), actionResult, buildId, details));
    }

    /**
     * Get the most recent entries that match the filter with the newest first.
     */
    public List<AuditEntry> getRecentEntries(AuditFilter filter) {
        return repo.findFiltered(filter.target(), filter.actionType(), filter.username(), filter.actionResult(),
                Limit.of(maxEntries));
    }

    /**
     * Who started a build of a target through the dashboard.
     */
    public Optional<String> findBuildStarter(String target, String buildId) {
        return repo.findFirstByTargetAndActionTypeAndBuildIdOrderByIdDesc(target, AuditEntry.ActionType.TRIGGER_BUILD,
                buildId).map(AuditEntry::getUsername);
    }
}
