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

    /** Get the most recent entries that match the filter, newest first. */
    public List<AuditEntry> getRecentEntries(AuditFilter filter) {
        return entries.findFiltered(filter.target(), filter.actionType(), filter.username(), filter.actionResult(),
                Limit.of(maxEntries));
    }

    /**
     * The values in the log that each filter can pick, sorted: only ones that match at least one entry.
     */
    public AuditFilterOptions getFilterOptions() {
        return new AuditFilterOptions(entries.findTargets(), entries.findActionTypes(), entries.findUsernames());
    }

    public record AuditFilterOptions(List<String> targets, List<AuditEntry.ActionType> actionTypes,
                                     List<String> usernames) {
    }

    /** Who started a build of a target (e.g. the pipeline "jenkins:sample-app") through the dashboard, if anyone. */
    public Optional<String> findBuildStarter(String target, String buildId) {
        return entries.findFirstByTargetAndActionTypeAndBuildIdOrderByIdDesc(target, AuditEntry.ActionType.TRIGGER_BUILD,
                buildId).map(AuditEntry::getUsername);
    }
}
