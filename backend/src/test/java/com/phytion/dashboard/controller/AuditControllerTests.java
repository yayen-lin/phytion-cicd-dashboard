package com.phytion.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.phytion.dashboard.model.AuditEntry;
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
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable"));

        mvc.perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].actionResult").value("FAILED"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-03T11:00:00Z"))
                .andExpect(jsonPath("$[0].details").value("Jenkins not reachable"))
                .andExpect(jsonPath("$[1].username").value("andy"))
                .andExpect(jsonPath("$[1].actionType").value("TRIGGER_BUILD"))
                .andExpect(jsonPath("$[1].target").value("jenkins:sample-app"))
                .andExpect(jsonPath("$[1].buildId").value("3"));
    }

    @Test
    void findBuildStarterFindsTheDashboardUserWhoStartedTheBuild() {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null));

        assertThat(auditService.findBuildStarter("jenkins:sample-app", "3")).contains("andy");
        assertThat(auditService.findBuildStarter("jenkins:sample-app", "4")).isEmpty();
        assertThat(auditService.findBuildStarter("jenkins:other-app", "3")).isEmpty();
    }

    @Test
    @WithMockUser
    void auditLogCanBeFilteredByTarget() throws Exception {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:other-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.SUCCESS, "7", null));

        mvc.perform(get("/api/audit").param("target", "jenkins:sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].buildId").value("3"));
    }

    @Test
    @WithMockUser
    void auditLogFiltersCombine() throws Exception {
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T10:00:00Z"), AuditEntry.ActionResult.SUCCESS, "3", null));
        entries.save(new AuditEntry("andy", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T11:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable"));
        entries.save(new AuditEntry("sam", AuditEntry.ActionType.TRIGGER_BUILD, "jenkins:sample-app",
                Instant.parse("2026-11-03T12:00:00Z"), AuditEntry.ActionResult.FAILED, null, "Jenkins not reachable"));

        mvc.perform(get("/api/audit").param("username", "andy").param("actionResult", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-03T11:00:00Z"));
        mvc.perform(get("/api/audit").param("actionType", "TRIGGER_BUILD"))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @WithMockUser
    void unknownActionTypeIsABadRequest() throws Exception {
        mvc.perform(get("/api/audit").param("actionType", "NOPE")).andExpect(status().isBadRequest());
    }
}
