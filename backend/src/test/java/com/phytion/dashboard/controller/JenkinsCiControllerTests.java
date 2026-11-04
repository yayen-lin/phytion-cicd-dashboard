package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.model.JenkinsCiBuildEntry;
import com.phytion.dashboard.service.JenkinsCiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the HTTP side only (login, CSRF, JSON, status codes), with a fake CiService.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class JenkinsCiControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JenkinsCiService ciService;

    @Test
    void listBuildsNeedsLogin() throws Exception {
        mvc.perform(get("/api/ci/builds")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listBuildsReturnsTheJobAndItsBuilds() throws Exception {
        when(ciService.job()).thenReturn("sample-app");
        when(ciService.listBuilds()).thenReturn(List.of(
                new JenkinsCiBuildEntry("2", JenkinsCiBuildEntry.Status.SUCCESS, "cc99f6b", "main", Instant.parse("2026-11-03T10:00:00Z"),
                        5516L, "http://jenkins/job/sample-app/2/")));

        mvc.perform(get("/api/ci/builds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job").value("sample-app"))
                .andExpect(jsonPath("$.builds[0].id").value("2"))
                .andExpect(jsonPath("$.builds[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.builds[0].commit").value("cc99f6b"))
                .andExpect(jsonPath("$.builds[0].startedAt").value("2026-11-03T10:00:00Z"))
                .andExpect(jsonPath("$.builds[0].durationMillis").value(5516));
    }

    @Test
    @WithMockUser
    void ciToolFailureGives502WithTheReason() throws Exception {
        when(ciService.listBuilds()).thenThrow(new CiToolException("Jenkins not reachable"));

        mvc.perform(get("/api/ci/builds"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jenkins not reachable"));
    }

    @Test
    void triggerBuildNeedsLogin() throws Exception {
        mvc.perform(post("/api/ci/builds").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void triggerBuildNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/ci/builds")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void triggerBuildPassesTheLoggedInUserAndReturnsTheBuildId() throws Exception {
        when(ciService.triggerBuild("andy")).thenReturn(Optional.of("4"));

        mvc.perform(post("/api/ci/builds").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").value("4"));
    }

    @Test
    @WithMockUser(username = "andy")
    void triggerBuildStillQueuedReturnsNullBuildId() throws Exception {
        when(ciService.triggerBuild("andy")).thenReturn(Optional.empty());

        mvc.perform(post("/api/ci/builds").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").isEmpty());
    }
}
