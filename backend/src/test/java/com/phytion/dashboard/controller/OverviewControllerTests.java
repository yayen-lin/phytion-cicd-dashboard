package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.model.EnvironmentOverview;
import com.phytion.dashboard.service.OverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the overview API with a faked service.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class OverviewControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OverviewService overviewService;

    @Test
    void overviewNeedsLogin() throws Exception {
        mvc.perform(get("/api/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void overviewReturnsEachEnvironmentsAppsAndRunningDeploys() throws Exception {
        when(overviewService.listEnvironmentOverviews()).thenReturn(List.of(
                new EnvironmentOverview("prod",
                        List.of(new EnvironmentApp("sample-app", "1.0.1", null, null, 2, 1, false,
                                Instant.parse("2026-11-14T10:00:00Z"), "andy", "7")),
                        List.of(new EnvironmentOverview.RunningDeploy("sample-app", "1.0.2",
                                Instant.parse("2026-11-14T11:00:00Z"), "andy", "8",
                                "http://localhost:8081/job/sample-app-deploy/8/")),
                        List.of()),
                new EnvironmentOverview("dev", null, List.of(), List.of(EnvironmentOverview.Part.APPS))));

        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments[0].environment").value("prod"))
                .andExpect(jsonPath("$.environments[0].apps[0].appId").value("sample-app"))
                .andExpect(jsonPath("$.environments[0].apps[0].readyPods").value(1))
                .andExpect(jsonPath("$.environments[0].apps[0].deployedBy").value("andy"))
                .andExpect(jsonPath("$.environments[0].runningDeploys[0].version").value("1.0.2"))
                .andExpect(jsonPath("$.environments[1].apps").doesNotExist())
                .andExpect(jsonPath("$.environments[1].unavailable[0]").value("APPS"));
    }
}
