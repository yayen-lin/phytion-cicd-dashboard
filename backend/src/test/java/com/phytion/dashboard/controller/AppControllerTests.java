package com.phytion.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the apps API against the real config, so it also checks how dashboard.apps is read.
 */
@SpringBootTest(properties = {
        "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties",
        "BITBUCKET_SAMPLE_APP_TOKEN=secret-token",
})
@AutoConfigureMockMvc
class AppControllerTests {

    @Autowired
    private MockMvc mvc;

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
    void listAppsNeverSendsTheRepoToken() throws Exception {
        mvc.perform(get("/api/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].repo.token").doesNotExist());
    }
}
