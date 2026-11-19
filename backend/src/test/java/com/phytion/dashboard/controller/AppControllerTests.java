package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AppSummary;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.EnvironmentApp;
import com.phytion.dashboard.service.AppService;
import com.phytion.dashboard.service.AppSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the apps API against the real config, so it also checks how dashboard.apps is read. The summary's service
 * is faked.
 */
@SpringBootTest(properties = "BITBUCKET_SAMPLE_APP_TOKEN=secret-token")
@AutoConfigureMockMvc
class AppControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppService appService;

    // the summary's parts come from Jenkins, the registry and Kubernetes: faked here
    @MockitoBean
    private AppSummaryService appSummaryService;

    @Test
    void listAppsNeedsLogin() throws Exception {
        mvc.perform(get("/api/apps")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listAppsReturnsEachAppsRepoPipelineAndReleaseInConfigOrder() throws Exception {
        mvc.perform(get("/api/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sample-app"))
                .andExpect(jsonPath("$[0].repo.tool").value("bitbucket"))
                .andExpect(jsonPath("$[0].repo.workspace").value("andy-test-app"))
                .andExpect(jsonPath("$[0].repo.name").value("sample-app"))
                .andExpect(jsonPath("$[0].pipeline.tool").value("jenkins"))
                .andExpect(jsonPath("$[0].pipeline.job").value("sample-app"))
                .andExpect(jsonPath("$[0].release.tool").value("docker-registry"))
                .andExpect(jsonPath("$[0].release.image").value("sample-app"))
                .andExpect(jsonPath("$[1].id").value("dummy-app"));
    }

    @Test
    @WithMockUser
    void getAppReturnsOneAppWithoutItsRepoToken() throws Exception {
        mvc.perform(get("/api/apps/sample-app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sample-app"))
                .andExpect(jsonPath("$.repo.workspace").value("andy-test-app"))
                .andExpect(jsonPath("$.pipeline.job").value("sample-app"))
                .andExpect(jsonPath("$.repo.token").doesNotExist());
    }

    @Test
    @WithMockUser
    void getUnknownAppGives404WithTheReason() throws Exception {
        mvc.perform(get("/api/apps/other-app"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown app: other-app"));
    }

    @Test
    void summaryNeedsLogin() throws Exception {
        mvc.perform(get("/api/apps/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void summaryHasTheEnvironmentsAndEachAppsPartsAndWhatIsUnavailable() throws Exception {
        App app = appService.getApp("sample-app");
        when(appSummaryService.listAppSummaries()).thenReturn(List.of(new AppSummary(app,
                new CiBuild("12", CiBuild.Status.SUCCESS, null, "main", Instant.parse("2026-11-13T18:00:00Z"), 42_000L,
                        "http://localhost:8081/job/sample-app/12/", null),
                null,
                List.of(new AppSummary.Deployment("dev", new EnvironmentApp("sample-app", "1.0.1", null, null, 1, 1,
                        true, null, null, null))),
                List.of(AppSummary.Part.VERSION))));

        mvc.perform(get("/api/apps/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environments[2].name").value("prod"))
                .andExpect(jsonPath("$.environments[2].needsConfirmation").value(true))
                .andExpect(jsonPath("$.apps[0].app.id").value("sample-app"))
                .andExpect(jsonPath("$.apps[0].app.repo.token").doesNotExist())
                .andExpect(jsonPath("$.apps[0].latestBuild.id").value("12"))
                .andExpect(jsonPath("$.apps[0].latestVersion").isEmpty())
                .andExpect(jsonPath("$.apps[0].deployments[0].environment").value("dev"))
                .andExpect(jsonPath("$.apps[0].deployments[0].state.version").value("1.0.1"))
                .andExpect(jsonPath("$.apps[0].unavailable[0]").value("VERSION"));
    }

    @Test
    @WithMockUser
    void listAppsNeverSendsTheRepoToken() throws Exception {
        mvc.perform(get("/api/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repo.token").doesNotExist());
    }
}
