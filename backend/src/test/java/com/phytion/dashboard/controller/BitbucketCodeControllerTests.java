package com.phytion.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.service.BitbucketCodeService;
import com.phytion.dashboard.service.RepoNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests the HTTP side only (login, JSON, status codes), with a fake BitbucketCodeService.
 */
@SpringBootTest(properties = "DASHBOARD_USERS_FILE=src/test/resources/test-users.properties")
@AutoConfigureMockMvc
class BitbucketCodeControllerTests {

    private static final String REPO = "bitbucket:andy-test-app/sample-app";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BitbucketCodeService codeService;

    @Test
    void listReposNeedsLogin() throws Exception {
        mvc.perform(get("/api/code/repos")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listReposReturnsIdToolAndRepo() throws Exception {
        when(codeService.listRepos()).thenReturn(List.of(new CodeRepo("bitbucket", "andy-test-app", "sample-app", null)));

        mvc.perform(get("/api/code/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(REPO))
                .andExpect(jsonPath("$[0].tool").value("bitbucket"))
                .andExpect(jsonPath("$[0].workspace").value("andy-test-app"))
                .andExpect(jsonPath("$[0].name").value("sample-app"));
    }

    @Test
    @WithMockUser
    void listBranchesReturnsTheRepoAndItsBranches() throws Exception {
        when(codeService.listBranches(REPO)).thenReturn(List.of(
                new CodeBranch("main", commit(), "https://bitbucket.org/andy-test-app/sample-app/branch/main")));

        mvc.perform(get("/api/code/branches").param("repoId", REPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repo").value(REPO))
                .andExpect(jsonPath("$.branches[0].name").value("main"))
                .andExpect(jsonPath("$.branches[0].latestCommit.hash").value("cc99f6b"))
                .andExpect(jsonPath("$.branches[0].latestCommit.committedAt").value("2026-11-03T22:11:13Z"));
    }

    @Test
    @WithMockUser
    void listCommitsReturnsTheRepoBranchAndCommits() throws Exception {
        when(codeService.listCommits(REPO, "main")).thenReturn(List.of(commit()));

        mvc.perform(get("/api/code/commits").param("repoId", REPO).param("branch", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.commits[0].hash").value("cc99f6b"))
                .andExpect(jsonPath("$.commits[0].author").value("Andy Lin"));
    }

    @Test
    @WithMockUser
    void listCommitsNeedsABranch() throws Exception {
        mvc.perform(get("/api/code/commits").param("repoId", REPO)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void unknownRepoGives404WithTheReason() throws Exception {
        when(codeService.listBranches("bitbucket:x/y")).thenThrow(new RepoNotFoundException("bitbucket:x/y"));

        mvc.perform(get("/api/code/branches").param("repoId", "bitbucket:x/y"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown repo: bitbucket:x/y"));
    }

    @Test
    @WithMockUser
    void codeToolFailureGives502WithTheReason() throws Exception {
        when(codeService.listBranches(REPO)).thenThrow(new CodeToolException("Bitbucket not reachable"));

        mvc.perform(get("/api/code/branches").param("repoId", REPO))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Bitbucket not reachable"));
    }

    private static CodeCommit commit() {
        return new CodeCommit("cc99f6b", "sample app initial commit\n", "Andy Lin",
                Instant.parse("2026-11-03T22:11:13Z"), "https://bitbucket.org/andy-test-app/sample-app/commits/cc99f6b");
    }
}
