package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
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
        codeService = new BitbucketCodeService(code, new CodeProperties(List.of(
                new CodeRepo("bitbucket", "andy-test-app/sample-app", "sample-app-token"),
                new CodeRepo("bitbucket", "andy-test-app/dummy-app", " "))));
    }

    @Test
    void createTagUsesTheReposToken() {
        codeService.createTag("bitbucket:andy-test-app/sample-app", "v1.4.0", "a4dad00");

        verify(code).createTag("andy-test-app/sample-app", "sample-app-token", "v1.4.0", "a4dad00");
    }

    @Test
    void createTagWithoutATokenSaysSoAndDoesNotAskTheTool() {
        // A blank value: the env var behind ${BITBUCKET_..._TOKEN:} isn't set
        assertThatThrownBy(() -> codeService.createTag("bitbucket:andy-test-app/dummy-app", "v1.4.0", "a4dad00"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("No token is set for bitbucket:andy-test-app/dummy-app, "
                        + "so the dashboard can't create tags there");
        verifyNoInteractions(code);
    }

    @Test
    void repoNeverShowsItsTokenAsText() {
        assertThat(codeService.repos().getFirst().toString())
                .doesNotContain("sample-app-token")
                .contains("token=(set)");
        assertThat(codeService.repos().get(1).toString()).contains("token=(none)");
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
    void commitUrlAsksTheToolForTheRepoAndCommit() {
        when(code.commitUrl("andy-test-app/sample-app", "a4dad00")).thenReturn("https://bitbucket.org/x");

        assertThat(codeService.commitUrl("bitbucket:andy-test-app/sample-app", "a4dad00"))
                .isEqualTo("https://bitbucket.org/x");
    }

    @Test
    void unknownRepoIsRejected() {
        assertThatThrownBy(() -> codeService.listBranches("bitbucket:andy-test-app/other-app"))
                .isInstanceOf(RepoNotFoundException.class);
        assertThatThrownBy(() -> codeService.listCommits("bitbucket:andy-test-app/other-app", "main"))
                .isInstanceOf(RepoNotFoundException.class);
        assertThatThrownBy(() -> codeService.commitUrl("bitbucket:andy-test-app/other-app", "a4dad00"))
                .isInstanceOf(RepoNotFoundException.class);
        assertThatThrownBy(() -> codeService.createTag("bitbucket:andy-test-app/other-app", "v1.4.0", "a4dad00"))
                .isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void repoOnAToolWithoutAdapterFailsAtStartup() {
        CodeProperties properties = new CodeProperties(List.of(new CodeRepo("github", "phytion/some-app", null)));

        assertThatThrownBy(() -> new BitbucketCodeService(code, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("github");
    }

    @Test
    void repoWithoutWorkspaceFailsAtStartup() {
        CodeProperties properties = new CodeProperties(List.of(new CodeRepo("bitbucket", "sample-app", null)));

        assertThatThrownBy(() -> new BitbucketCodeService(code, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("workspace/repo");
    }
}
