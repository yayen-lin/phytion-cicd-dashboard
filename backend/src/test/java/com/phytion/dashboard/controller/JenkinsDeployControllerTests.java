package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
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

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JenkinsDeployService deployService;

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
}
