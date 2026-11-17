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
import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.Promotion;
import com.phytion.dashboard.model.Rollback;
import com.phytion.dashboard.model.RollbackOptions;
import com.phytion.dashboard.service.AppNotFoundException;
import com.phytion.dashboard.service.InvalidDeployException;
import com.phytion.dashboard.service.JenkinsDeployService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests the HTTP side only (login, JSON, status codes), with a fake JenkinsDeployService.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class JenkinsDeployControllerTests {

    private static final String APP = "sample-app";
    private static final String DEPLOY = """
            {"appId": "sample-app", "version": "1.0.1", "environment": "staging"}
            """;
    private static final String PROMOTE = """
            {"appId": "sample-app", "fromEnvironment": "dev"}
            """;
    private static final String ROLLBACK = """
            {"appId": "sample-app", "environment": "staging", "version": "1.0.0"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JenkinsDeployService deployService;

    @Test
    @WithMockUser
    void getDeployBuildReturnsItsStatusAndLink() throws Exception {
        when(deployService.getDeployBuild(APP, "3")).thenReturn(Optional.of(new CiBuild("3", CiBuild.Status.RUNNING,
                null, null, Instant.parse("2026-11-13T18:31:00Z"), null,
                "http://localhost:8081/job/sample-app-deploy/3/", null)));

        mvc.perform(get("/api/deploy/deployments/3").param("appId", APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.url").value("http://localhost:8081/job/sample-app-deploy/3/"));
    }

    @Test
    @WithMockUser
    void getDeployBuildThatDoesNotExistGives404() throws Exception {
        when(deployService.getDeployBuild(APP, "99")).thenReturn(Optional.empty());

        mvc.perform(get("/api/deploy/deployments/99").param("appId", APP)).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getRollbackOptionsReturnsTheFromVersionAndTheVersionsToGoTo() throws Exception {
        when(deployService.listRollbackOptions(APP, "staging"))
                .thenReturn(new RollbackOptions(APP, "staging", "1.0.2", List.of("1.0.0", "0.0.9")));

        mvc.perform(get("/api/deploy/rollback-options").param("appId", APP).param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP))
                .andExpect(jsonPath("$.environment").value("staging"))
                .andExpect(jsonPath("$.fromVersion").value("1.0.2"))
                .andExpect(jsonPath("$.toVersions[0]").value("1.0.0"))
                .andExpect(jsonPath("$.toVersions[1]").value("0.0.9"));
    }

    @Test
    @WithMockUser
    void getRollbackOptionsWithNothingToGoBackToGivesAnEmptyList() throws Exception {
        when(deployService.listRollbackOptions(APP, "staging"))
                .thenReturn(new RollbackOptions(APP, "staging", "1.0.0", List.of()));

        mvc.perform(get("/api/deploy/rollback-options").param("appId", APP).param("environment", "staging"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toVersions").isEmpty());
    }

    @Test
    @WithMockUser
    void getRollbackOptionsWithoutEnvironmentGives400() throws Exception {
        mvc.perform(get("/api/deploy/rollback-options").param("appId", APP)).andExpect(status().isBadRequest());
    }

    @Test
    void getRollbackOptionsNeedsLogin() throws Exception {
        mvc.perform(get("/api/deploy/rollback-options").param("appId", APP).param("environment", "staging"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deployNeedsLogin() throws Exception {
        mvc.perform(postDeploy(DEPLOY).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void deployNeedsCsrfToken() throws Exception {
        mvc.perform(postDeploy(DEPLOY)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void deployPassesTheLoggedInUserAndReturnsTheBuildId() throws Exception {
        when(deployService.deploy(APP, "1.0.1", "staging", "andy")).thenReturn(Optional.of("3"));

        mvc.perform(postDeploy(DEPLOY).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value(APP))
                .andExpect(jsonPath("$.version").value("1.0.1"))
                .andExpect(jsonPath("$.environment").value("staging"))
                .andExpect(jsonPath("$.buildId").value("3"));
    }

    @Test
    @WithMockUser(username = "andy")
    void deployWhoseBuildIsStillQueuedReturnsNullBuildId() throws Exception {
        when(deployService.deploy(APP, "1.0.1", "staging", "andy")).thenReturn(Optional.empty());

        mvc.perform(postDeploy(DEPLOY).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").isEmpty());
    }

    @Test
    @WithMockUser
    void deployWithoutEnvironmentGives400() throws Exception {
        mvc.perform(postDeploy("""
                {"appId": "sample-app", "version": "1.0.1"}
                """).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "andy")
    void versionNotReleasedGives400WithTheReason() throws Exception {
        when(deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .thenThrow(new InvalidDeployException("sample-app has no released version 1.0.1"));

        mvc.perform(postDeploy(DEPLOY).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sample-app has no released version 1.0.1"));
    }

    @Test
    @WithMockUser(username = "andy")
    void unknownAppGives404WithTheReason() throws Exception {
        when(deployService.deploy(APP, "1.0.1", "staging", "andy")).thenThrow(new AppNotFoundException(APP));

        mvc.perform(postDeploy(DEPLOY).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown app: sample-app"));
    }

    @Test
    @WithMockUser(username = "andy")
    void deployJobThatDidNotStartGives502WithTheReason() throws Exception {
        when(deployService.deploy(APP, "1.0.1", "staging", "andy"))
                .thenThrow(new CiToolException("Jenkins not reachable"));

        mvc.perform(postDeploy(DEPLOY).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jenkins not reachable"));
    }

    private static MockHttpServletRequestBuilder postDeploy(String json) {
        return post("/api/deploy/deployments").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void promoteNeedsLogin() throws Exception {
        mvc.perform(postPromote(PROMOTE).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void promoteNeedsCsrfToken() throws Exception {
        mvc.perform(postPromote(PROMOTE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void promotePassesTheLoggedInUserAndReturnsThePromotion() throws Exception {
        when(deployService.promote(APP, "dev", "andy"))
                .thenReturn(new Promotion(APP, "1.0.1", "dev", "staging", "4"));

        mvc.perform(postPromote(PROMOTE).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP))
                .andExpect(jsonPath("$.version").value("1.0.1"))
                .andExpect(jsonPath("$.fromEnvironment").value("dev"))
                .andExpect(jsonPath("$.toEnvironment").value("staging"))
                .andExpect(jsonPath("$.buildId").value("4"));
    }

    @Test
    @WithMockUser
    void promoteWithoutEnvironmentGives400() throws Exception {
        mvc.perform(postPromote("""
                {"appId": "sample-app"}
                """).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "andy")
    void promoteWithNothingRunningGives400WithTheReason() throws Exception {
        when(deployService.promote(APP, "dev", "andy"))
                .thenThrow(new InvalidDeployException("sample-app has no version running in dev"));

        mvc.perform(postPromote(PROMOTE).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sample-app has no version running in dev"));
    }

    @Test
    @WithMockUser(username = "andy")
    void promoteWhenKubernetesIsDownGives502WithTheReason() throws Exception {
        when(deployService.promote(APP, "dev", "andy"))
                .thenThrow(new OperateToolException("Kubernetes not reachable"));

        mvc.perform(postPromote(PROMOTE).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Kubernetes not reachable"));
    }

    private static MockHttpServletRequestBuilder postPromote(String json) {
        return post("/api/deploy/promotions").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    @Test
    void rollbackNeedsLogin() throws Exception {
        mvc.perform(postRollback(ROLLBACK).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void rollbackNeedsCsrfToken() throws Exception {
        mvc.perform(postRollback(ROLLBACK)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void rollbackPassesTheLoggedInUserAndReturnsTheRollback() throws Exception {
        when(deployService.rollback(APP, "staging", "1.0.0", "andy"))
                .thenReturn(new Rollback(APP, "staging", "1.0.2", "1.0.0", "7"));

        mvc.perform(postRollback(ROLLBACK).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appId").value(APP))
                .andExpect(jsonPath("$.environment").value("staging"))
                .andExpect(jsonPath("$.fromVersion").value("1.0.2"))
                .andExpect(jsonPath("$.toVersion").value("1.0.0"))
                .andExpect(jsonPath("$.buildId").value("7"));
    }

    @Test
    @WithMockUser
    void rollbackWithoutEnvironmentOrVersionGives400() throws Exception {
        mvc.perform(postRollback("""
                {"appId": "sample-app", "version": "1.0.0"}
                """).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(postRollback("""
                {"appId": "sample-app", "environment": "staging"}
                """).with(csrf())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "andy")
    void rollbackWithNothingOlderGives400WithTheReason() throws Exception {
        when(deployService.rollback(APP, "staging", "1.0.0", "andy")).thenThrow(
                new InvalidDeployException("sample-app has no older version to roll back to in staging"));

        mvc.perform(postRollback(ROLLBACK).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("sample-app has no older version to roll back to in staging"));
    }

    @Test
    @WithMockUser(username = "andy")
    void rollbackWhenJenkinsIsDownGives502WithTheReason() throws Exception {
        when(deployService.rollback(APP, "staging", "1.0.0", "andy"))
                .thenThrow(new CiToolException("Jenkins not reachable"));

        mvc.perform(postRollback(ROLLBACK).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Jenkins not reachable"));
    }

    private static MockHttpServletRequestBuilder postRollback(String json) {
        return post("/api/deploy/rollbacks").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
