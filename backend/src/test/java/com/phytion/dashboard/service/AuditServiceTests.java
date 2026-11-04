package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.repository.AuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the audit log logic with a fake repository: no Spring, no database.
 */
class AuditServiceTests {

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        AuditEntryRepository entries = mock(AuditEntryRepository.class);
        // save() returns what it was given, like the real repository does
        when(entries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        auditService = new AuditService(entries, 50);
    }

    @Test
    void addTimestampsTheEntry() {
        Instant before = Instant.now();

        AuditEntry entry = auditService.add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app",
                AuditEntry.ActionResult.SUCCESS, "4", null);

        assertThat(entry.getUsername()).isEqualTo("andy");
        assertThat(entry.getBuildId()).isEqualTo("4");
        assertThat(entry.getCreatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void addCutsLongDetailsToFitTheColumn() {
        AuditEntry entry = auditService.add("andy", AuditEntry.ActionType.TRIGGER_BUILD, "sample-app",
                AuditEntry.ActionResult.FAILED, null, "x".repeat(5000));

        assertThat(entry.getDetails()).hasSize(1000);
    }
}
