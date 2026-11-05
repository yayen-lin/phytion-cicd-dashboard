package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.config.CodeProperties;
import com.phytion.dashboard.model.CodeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the code logic with a fake code tool: no Spring.
 */
class BitbucketCodeServiceTests {

    private BitbucketAdapter code;
    private BitbucketCodeService codeService;

    @BeforeEach
    void setUp() {
        code = mock(BitbucketAdapter.class);
        codeService = new BitbucketCodeService(code,
                new CodeProperties(List.of(new CodeRepo("bitbucket", "andy-test-app/sample-app"))));
    }

    @Test
    void listBranchesAsksTheToolForTheRepo() {
        when(code.listBranches("andy-test-app/sample-app")).thenReturn(List.of());

        assertThat(codeService.listBranches("bitbucket:andy-test-app/sample-app")).isEmpty();

        verify(code).listBranches("andy-test-app/sample-app");
    }

    @Test
    void listCommitsAsksTheToolForTheRepoAndBranch() {
        when(code.listCommits("andy-test-app/sample-app", "main")).thenReturn(List.of());

        assertThat(codeService.listCommits("bitbucket:andy-test-app/sample-app", "main")).isEmpty();

        verify(code).listCommits("andy-test-app/sample-app", "main");
    }

    @Test
    void unknownRepoIsRejected() {
        assertThatThrownBy(() -> codeService.listBranches("bitbucket:andy-test-app/other-app"))
                .isInstanceOf(RepoNotFoundException.class);
        assertThatThrownBy(() -> codeService.listCommits("bitbucket:andy-test-app/other-app", "main"))
                .isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void repoOnAToolWithoutAdapterFailsAtStartup() {
        CodeProperties properties = new CodeProperties(List.of(new CodeRepo("github", "phytion/some-app")));

        assertThatThrownBy(() -> new BitbucketCodeService(code, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("github");
    }

    @Test
    void repoWithoutWorkspaceFailsAtStartup() {
        CodeProperties properties = new CodeProperties(List.of(new CodeRepo("bitbucket", "sample-app")));

        assertThatThrownBy(() -> new BitbucketCodeService(code, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspace/repo");
    }
}
