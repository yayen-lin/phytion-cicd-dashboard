package com.phytion.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.AuditFilter;
import com.phytion.dashboard.repository.AuditEntryRepository;
import com.phytion.dashboard.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the audit log API all the way down to the (in-memory) database, so it also checks that the Flyway
 * migrations and the AuditEntry class match.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class AuditControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditEntryRepository entries;

    @Autowired
    private AuditService auditService;

    @BeforeEach
    void clearAuditLog() {
        entries.deleteAll();
    }

    @Test
    void auditLogNeedsLogin() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void auditLogIsNewestFirst() throws Exception {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null, null, null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable", null, null));

        mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].actionResult").value("FAILED"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-03T11:00:00Z"))
                .andExpect(jsonPath("$[0].details").value("Jenkins not reachable"))
                .andExpect(jsonPath("$[1].username").value("andy"))
                .andExpect(jsonPath("$[1].actionType").value("BUILD"))
                .andExpect(jsonPath("$[1].target").value("jenkins:sample-app"))
                .andExpect(jsonPath("$[1].buildId").value("3"));
    }

    @Test
    void recentEntriesMatchBuildEnvironmentAndVersionNewestByTimestamp() {
        // saved out of time order, so the order comes from the timestamp, not the id
        entries.save(new AuditEntry("sam", AuditEntry.ActionType.DEPLOY, "sample-app",
                Instant.parse("2026-11-03T12:00:00Z"), AuditEntry.ActionResult.SUCCESS, "5", null, "staging", "1.0.1"));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.DEPLOY, "sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null, "staging", "1.0.1"));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.DEPLOY, "sample-app",
                Instant.parse("2026-11-03T13:00:00Z"), AuditEntry.ActionResult.SUCCESS, "6", null, "dev", "1.0.1"));

        assertThat(auditService.getRecentEntries(new AuditFilter("sample-app", AuditEntry.ActionType.DEPLOY, null,
                AuditEntry.ActionResult.SUCCESS, null, "staging", "1.0.1")))
                .extracting(AuditEntry::getBuildId).containsExactly("5", "3");
        assertThat(auditService.getRecentEntries(new AuditFilter(null, null, null, null, "3", null, null)))
                .extracting(AuditEntry::getUsername).containsExactly("andy");
        assertThat(auditService.getRecentEntries(new AuditFilter(null, null, null, null, null, "prod", null))).isEmpty();
    }

    @Test
    @WithMockUser
    void auditLogCanBeFilteredByTarget() throws Exception {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null, null, null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:other-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.SUCCESS, "7", null, null, null));

        mvc.perform(get("/api/audit").param("target", "jenkins:sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buildId").value("3"));
    }

    @Test
    @WithMockUser
    void auditLogFiltersCombine() throws Exception {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null, null, null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable", null, null));
        entries.save(new AuditEntry("sam", AuditEntry.ActionType.BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T12:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable", null, null));

        mvc.perform(get("/api/audit").param("username", "andy").param("actionResult", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-03T11:00:00Z"));
        mvc.perform(get("/api/audit").param("actionType", "BUILD"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser
    void unknownActionTypeIsABadRequest() throws Exception {
        mvc.perform(get("/api/audit").param("actionType", "NOPE")).andExpect(status().isBadRequest());
    }
}
