package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.config.AppsProperties;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeBranchCommits;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the code logic with a fake code tool: no Spring.
 */
class BitbucketCodeServiceTests {

    private static final String WORKSPACE = "andy-test-app";
    private static final String REPO = "sample-app";
    private static final CodeCommit COMMIT = new CodeCommit("cc99f6b", "sample app initial commit", "Andy Lin",
            Instant.parse("2026-11-03T22:11:13Z"), "https://bitbucket.org/andy-test-app/sample-app/commits/cc99f6b");

    private BitbucketAdapter code;
    private BitbucketCodeService codeService;

    @BeforeEach
    void setUp() {
        code = mock(BitbucketAdapter.class);
        codeService = new BitbucketCodeService(code, new AppService(new AppsProperties(Map.of(
                "sample-app", app(new CodeRepo("bitbucket", "andy-test-app", "sample-app", "sample-app-token"))))));
    }

    @Test
    void createTagUsesTheReposToken() {
        codeService.createTag("sample-app", "v1.4.0", "a4dad00");

        verify(code).createTag("andy-test-app", "sample-app", "sample-app-token", "v1.4.0", "a4dad00");
    }

    @Test
    void createTagWithoutATokenSaysSoAndDoesNotAskTheTool() {
        // A blank value: the env var behind ${BITBUCKET_..._TOKEN:} isn't set
        codeService = new BitbucketCodeService(code, new AppService(new AppsProperties(Map.of(
                "dummy-app", app(new CodeRepo("bitbucket", "andy-test-app", "dummy-app", " "))))));

        assertThatThrownBy(() -> codeService.createTag("dummy-app", "v1.4.0", "a4dad00"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("No token is set for dummy-app's repo (andy-test-app/dummy-app), "
                        + "so the dashboard can't create tags there");
        verifyNoInteractions(code);
    }

    @Test
    void repoNeverShowsItsTokenAsText() {
        assertThat(new CodeRepo("bitbucket", "andy-test-app", "sample-app", "sample-app-token").toString())
                .doesNotContain("sample-app-token")
                .contains("token=(set)");
        assertThat(new CodeRepo("bitbucket", "andy-test-app", "dummy-app", " ").toString()).contains("token=(none)");
    }

    @Test
    void listBranchesAsksTheToolForTheRepo() {
        when(code.listBranches("andy-test-app", "sample-app")).thenReturn(List.of());

        assertThat(codeService.listBranches("sample-app")).isEmpty();

        verify(code).listBranches("andy-test-app", "sample-app");
    }

    @Test
    void listBranchCommitsShowsTheWantedBranch() {
        when(code.listBranches(WORKSPACE, REPO)).thenReturn(List.of(branch("dev"), branch("main")));
        when(code.listCommits(WORKSPACE, REPO, "dev")).thenReturn(List.of(COMMIT));

        assertThat(codeService.listBranchCommits("sample-app", "dev"))
                .isEqualTo(new CodeBranchCommits(List.of(branch("dev"), branch("main")), "dev", List.of(COMMIT)));
        verify(code, times(1)).listCommits(WORKSPACE, REPO, "dev");
    }

    @Test
    void listBranchCommitsWithoutABranchShowsMain() {
        when(code.listBranches(WORKSPACE, REPO)).thenReturn(List.of(branch("dev"), branch("main")));
        when(code.listCommits(WORKSPACE, REPO, "main")).thenReturn(List.of(COMMIT));

        assertThat(codeService.listBranchCommits("sample-app", null).branch()).isEqualTo("main");
        verify(code, times(1)).listCommits(WORKSPACE, REPO, "main");
    }

    @Test
    void listBranchCommitsOfAGoneBranchShowsMainInstead() {
        when(code.listBranches(WORKSPACE, REPO)).thenReturn(List.of(branch("dev"), branch("main")));
        when(code.listCommits(WORKSPACE, REPO, "gone")).thenThrow(new CodeToolException("Bitbucket answered 404"));
        when(code.listCommits(WORKSPACE, REPO, "main")).thenReturn(List.of(COMMIT));

        CodeBranchCommits result = codeService.listBranchCommits("sample-app", "gone");

        assertThat(result.branch()).isEqualTo("main");
        assertThat(result.commits()).containsExactly(COMMIT);
    }

    @Test
    void listBranchCommitsWithoutMainOrMasterShowsTheNewestBranch() {
        when(code.listBranches(WORKSPACE, REPO)).thenReturn(List.of(branch("dev"), branch("feature-x")));
        when(code.listCommits(WORKSPACE, REPO, "dev")).thenReturn(List.of(COMMIT));

        assertThat(codeService.listBranchCommits("sample-app", null).branch()).isEqualTo("dev");
    }

    @Test
    void listBranchCommitsOfARepoWithoutBranchesHasNoBranchOrCommits() {
        when(code.listBranches(WORKSPACE, REPO)).thenReturn(List.of());
        when(code.listCommits(WORKSPACE, REPO, "main")).thenThrow(new CodeToolException("Bitbucket answered 404"));

        assertThat(codeService.listBranchCommits("sample-app", null))
                .isEqualTo(new CodeBranchCommits(List.of(), null, List.of()));
    }

    @Test
    void listBranchCommitsFailsWhenTheBranchesCanNotBeRead() {
        when(code.listBranches(WORKSPACE, REPO)).thenThrow(new CodeToolException("Bitbucket not reachable"));

        assertThatThrownBy(() -> codeService.listBranchCommits("sample-app", null))
                .isInstanceOf(CodeToolException.class);
    }

    private static CodeBranch branch(String name) {
        return new CodeBranch(name, null, "https://bitbucket.org/andy-test-app/sample-app/branch/" + name);
    }

    @Test
    void commitUrlAsksTheToolForTheRepoAndGetCommit() {
        when(code.getCommitUrl("andy-test-app", "sample-app", "a4dad00")).thenReturn("https://bitbucket.org/x");

        assertThat(codeService.getCommitUrl("sample-app", "a4dad00"))
                .isEqualTo("https://bitbucket.org/x");
    }

    @Test
    void unknownAppIsRejected() {
        assertThatThrownBy(() -> codeService.listBranches("other-app"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> codeService.listBranchCommits("other-app", "main"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> codeService.getCommitUrl("other-app", "a4dad00"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> codeService.createTag("other-app", "v1.4.0", "a4dad00"))
                .isInstanceOf(AppNotFoundException.class);
    }

    private static AppsProperties.AppSettings app(CodeRepo repo) {
        return new AppsProperties.AppSettings(repo, new CiPipeline("jenkins", repo.name()), null, null);
    }
}
