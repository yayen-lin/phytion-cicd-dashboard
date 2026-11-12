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
import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.release.ReleaseToolException;
import com.phytion.dashboard.model.ReleaseVersion;
import com.phytion.dashboard.service.AppNotFoundException;
import com.phytion.dashboard.service.InvalidReleaseException;
import com.phytion.dashboard.service.DockerRegistryReleaseService;
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
 * Tests the HTTP side only (login, JSON, status codes), with a fake DockerRegistryReleaseService.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class DockerRegistryReleaseControllerTests {

    private static final String APP = "sample-app";
    private static final String COMMIT = "a4dad009a1f3ef48f7239a3dd155c64ca51d466f";
    private static final String RELEASE = """
            {"appId": "sample-app", "version": "v1.4.0", "commit": "a4dad009a1f3ef48f7239a3dd155c64ca51d466f"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DockerRegistryReleaseService releaseService;

    @Test
    void listVersionsNeedsLogin() throws Exception {
        mvc.perform(get("/api/release/versions").param("appId", APP)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listVersionsReturnsTheAppAndItsVersions() throws Exception {
        when(releaseService.listVersions(APP)).thenReturn(List.of(
                new ReleaseVersion("0.1.1", "11", "a4dad009a1f3ef48f7239a3dd155c64ca51d466f",
                        "https://bitbucket.org/andy-test-app/sample-app/commits/a4dad009a1f3ef48f7239a3dd155c64ca51d466f",
                        Instant.parse("2026-11-06T18:16:33Z")),
                new ReleaseVersion("0.1.0", null, null, null, null)));

        mvc.perform(get("/api/release/versions").param("appId", APP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value(APP))
                .andExpect(jsonPath("$.versions[0].version").value("0.1.1"))
                .andExpect(jsonPath("$.versions[0].buildId").value("11"))
                .andExpect(jsonPath("$.versions[0].commit").value("a4dad009a1f3ef48f7239a3dd155c64ca51d466f"))
                .andExpect(jsonPath("$.versions[0].commitUrl").value(
                        "https://bitbucket.org/andy-test-app/sample-app/commits/a4dad009a1f3ef48f7239a3dd155c64ca51d466f"))
                .andExpect(jsonPath("$.versions[0].createdAt").value("2026-11-06T18:16:33Z"))
                // Missing labels are sent as null, not left out
                .andExpect(jsonPath("$.versions[1].version").value("0.1.0"))
                .andExpect(jsonPath("$.versions[1].buildId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.versions[1].createdAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @WithMockUser
    void listVersionsNeedsAnApp() throws Exception {
        mvc.perform(get("/api/release/versions")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void unknownAppGives404WithTheReason() throws Exception {
        when(releaseService.listVersions("x")).thenThrow(new AppNotFoundException("x"));

        mvc.perform(get("/api/release/versions").param("appId", "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown app: x"));
    }

    @Test
    @WithMockUser
    void releaseToolFailureGives502WithTheReason() throws Exception {
        when(releaseService.listVersions(APP)).thenThrow(new ReleaseToolException("Registry not reachable"));

        mvc.perform(get("/api/release/versions").param("appId", APP))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Registry not reachable"));
    }

    @Test
    void releaseNeedsLogin() throws Exception {
        mvc.perform(postRelease(RELEASE).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void releaseNeedsCsrfToken() throws Exception {
        mvc.perform(postRelease(RELEASE)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "andy")
    void releasePassesTheLoggedInUserAndReturnsTheBuildId() throws Exception {
        when(releaseService.release(APP, "v1.4.0", COMMIT, "andy")).thenReturn(Optional.of("12"));

        mvc.perform(postRelease(RELEASE).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app").value(APP))
                .andExpect(jsonPath("$.version").value("v1.4.0"))
                .andExpect(jsonPath("$.buildId").value("12"));
    }

    @Test
    @WithMockUser(username = "andy")
    void releaseWhoseBuildIsStillQueuedReturnsNullBuildId() throws Exception {
        when(releaseService.release(APP, "v1.4.0", COMMIT, "andy")).thenReturn(Optional.empty());

        mvc.perform(postRelease(RELEASE).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildId").isEmpty());
    }

    @Test
    @WithMockUser(username = "andy")
    void badVersionGives400WithTheReason() throws Exception {
        when(releaseService.release(APP, "1.4", COMMIT, "andy"))
                .thenThrow(new InvalidReleaseException("A version looks like v1.4.0, got: 1.4"));

        mvc.perform(postRelease(RELEASE.replace("v1.4.0", "1.4")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("A version looks like v1.4.0, got: 1.4"));
    }

    @Test
    @WithMockUser(username = "andy")
    void tagRefusedGives502WithTheReason() throws Exception {
        when(releaseService.release(APP, "v1.4.0", COMMIT, "andy"))
                .thenThrow(new CodeToolException("Bitbucket refused the tag: Tag \"v1.4.0\" already exists"));

        mvc.perform(postRelease(RELEASE).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Bitbucket refused the tag: Tag \"v1.4.0\" already exists"));
    }

    @Test
    @WithMockUser(username = "andy")
    void buildThatDidNotStartGives502WithTheReason() throws Exception {
        String reason = "Tag v1.4.0 was created, but its build didn't start: Jenkins not reachable";
        when(releaseService.release(APP, "v1.4.0", COMMIT, "andy")).thenThrow(new CiToolException(reason));

        mvc.perform(postRelease(RELEASE).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value(reason));
    }

    private static MockHttpServletRequestBuilder postRelease(String json) {
        return post("/api/release/versions").contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
