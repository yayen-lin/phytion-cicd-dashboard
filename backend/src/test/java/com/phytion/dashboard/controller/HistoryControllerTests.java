package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.History;
import com.phytion.dashboard.service.HistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the history API with a faked service.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HistoryControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HistoryService historyService;

    @Test
    void historyNeedsLogin() throws Exception {
        mvc.perform(get("/api/history")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void historyReturnsEachEntryWithItsBuild() throws Exception {
        Instant at = Instant.parse("2026-11-14T10:00:00Z");
        when(historyService.listHistory()).thenReturn(new History(List.of(new History.Entry(
                new AuditEntry("andy", AuditEntry.ActionType.DEPLOY, "sample-app", at, AuditEntry.ActionResult.SUCCESS,
                        "3", "1.0.1 to prod", "prod", "1.0.1"),
                new CiBuild("3", CiBuild.Status.SUCCESS, null, null, at, 1000L,
                        "http://localhost:8081/job/sample-app-deploy/3/", null))), List.of()));

        mvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].entry.actionType").value("DEPLOY"))
                .andExpect(jsonPath("$.entries[0].entry.environment").value("prod"))
                .andExpect(jsonPath("$.entries[0].build.status").value("SUCCESS"))
                .andExpect(jsonPath("$.entries[0].build.url").value("http://localhost:8081/job/sample-app-deploy/3/"))
                .andExpect(jsonPath("$.unavailable").isEmpty());
    }
}
