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
import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CiTestCase;
import com.phytion.dashboard.model.CiTestCounts;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.service.PipelineNotFoundException;
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
    void listPipelinesNeedsLogin() throws Exception {
        mvc.perform(get("/api/ci/pipelines")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listPipelinesReturnsIdToolAndJob() throws Exception {
        when(ciService.listPipelines()).thenReturn(List.of(new CiPipeline("jenkins", "sample-app")));

        mvc.perform(get("/api/ci/pipelines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("jenkins:sample-app"))
                .andExpect(jsonPath("$[0].tool").value("jenkins"))
                .andExpect(jsonPath("$[0].job").value("sample-app"));
    }

    @Test
    void listBuildsNeedsLogin() throws Exception {
        mvc.perform(get("/api/ci/builds").param("pipelineId", "jenkins:sample-app")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listBuildsNeedsAPipeline() throws Exception {
        mvc.perform(get("/api/ci/builds")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void unknownPipelineGives404WithTheReason() throws Exception {
        when(ciService.listBuilds("jenkins:other-app")).thenThrow(new PipelineNotFoundException("jenkins:other-app"));

        mvc.perform(get("/api/ci/builds").param("pipelineId", "jenkins:other-app"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown pipeline: jenkins:other-app"));
    }

    @Test
    @WithMockUser
    void listBuildsReturnsThePipelineAndItsBuilds() throws Exception {
        when(ciService.listBuilds("jenkins:sample-app")).thenReturn(List.of(
                new CiBuild("2", CiBuild.Status.SUCCESS, "cc99f6b", "main", Instant.parse("2026-11-03T10:00:00Z"),
                        5516L, "http://jenkins/job/sample-app/2/", new CiTestCounts(5, 1, 0))));

        mvc.perform(get("/api/ci/builds").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("jenkins:sample-app"))
                .andExpect(jsonPath("$.builds[0].id").value("2"))
                .andExpect(jsonPath("$.builds[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.builds[0].commit").value("cc99f6b"))
                .andExpect(jsonPath("$.builds[0].startedAt").value("2026-11-03T10:00:00Z"))
                .andExpect(jsonPath("$.builds[0].durationMillis").value(5516))
                .andExpect(jsonPath("$.builds[0].tests.passed").value(5))
                .andExpect(jsonPath("$.builds[0].tests.failed").value(1));
    }

    @Test
    @WithMockUser
    void getTestReportReturnsCountsAndCases() throws Exception {
        when(ciService.getTestReport("jenkins:sample-app", "2")).thenReturn(Optional.of(new CiTestReport(
                new CiTestCounts(1, 1, 0), 941L, List.of(
                new CiTestCase("com.phytion.sampleapp.HelloControllerTests", "helloReturnsMessage()",
                        CiTestCase.Status.FAILED, 14L, "expected: <hello> but was: <hi>")))));

        mvc.perform(get("/api/ci/builds/2/tests").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.failed").value(1))
                .andExpect(jsonPath("$.durationMillis").value(941))
                .andExpect(jsonPath("$.cases[0].status").value("FAILED"))
                .andExpect(jsonPath("$.cases[0].errorMessage").value("expected: <hello> but was: <hi>"));
    }

    @Test
    @WithMockUser
    void getTestReportOfBuildWithoutTestsGives404() throws Exception {
        when(ciService.getTestReport("jenkins:sample-app", "3")).thenReturn(Optional.empty());

        mvc.perform(get("/api/ci/builds/3/tests").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void ciToolFailureGives502WithTheReason() throws Exception {
        when(ciService.listBuilds("jenkins:sample-app")).thenThrow(new CiToolException("Jenkins not reachable"));

        mvc.perform(get("/api/ci/builds").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jenkins not reachable"));
    }

    @Test
    @WithMockUser
    void getBuildReturnsTheBuildAndWhoStartedIt() throws Exception {
        when(ciService.getBuild("jenkins:sample-app", "2")).thenReturn(Optional.of(new CiBuildDetails(
                new CiBuild("2", CiBuild.Status.SUCCESS, "cc99f6b", "main",
                        Instant.parse("2026-11-03T10:00:00Z"), 5516L, "http://jenkins/job/sample-app/2/", null),
                "andy", "dashboard")));

        mvc.perform(get("/api/ci/builds/2").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.id").value("2"))
                .andExpect(jsonPath("$.build.status").value("SUCCESS"))
                .andExpect(jsonPath("$.startedBy").value("andy"))
                .andExpect(jsonPath("$.startedBySource").value("dashboard"));
    }

    @Test
    @WithMockUser
    void getBuildThatDoesNotExistGives404() throws Exception {
        when(ciService.getBuild("jenkins:sample-app", "99")).thenReturn(Optional.empty());

        mvc.perform(get("/api/ci/builds/99").param("pipelineId", "jenkins:sample-app"))
                .andExpect(status().isNotFound());
    }

    @Test
    void triggerBuildNeedsLogin() throws Exception {
        mvc.perform(post("/api/ci/builds").param("pipelineId", "jenkins:sample-app").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void triggerBuildNeedsCsrfToken() throws Exception {
        mvc.perform(post("/api/ci/builds").param("pipelineId", "jenkins:sample-app")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void triggerBuildPassesTheLoggedInUserAndReturnsTheBuildId() throws Exception {
        when(ciService.triggerBuild("jenkins:sample-app", "andy")).thenReturn(Optional.of("4"));

        mvc.perform(post("/api/ci/builds").param("pipelineId", "jenkins:sample-app").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").value("4"));
    }

    @Test
    @WithMockUser(username = "andy")
    void triggerBuildStillQueuedReturnsNullBuildId() throws Exception {
        when(ciService.triggerBuild("jenkins:sample-app", "andy")).thenReturn(Optional.empty());

        mvc.perform(post("/api/ci/builds").param("pipelineId", "jenkins:sample-app").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").isEmpty());
    }
}
