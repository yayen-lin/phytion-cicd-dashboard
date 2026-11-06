package com.phytion.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.phytion.dashboard.adapter.ci.CiToolException;
import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.release.registry.RegistryAdapter;
import com.phytion.dashboard.adapter.release.registry.RegistryProperties;
import com.phytion.dashboard.config.CiProperties;
import com.phytion.dashboard.config.CodeProperties;
import com.phytion.dashboard.config.ReleaseProperties;
import com.phytion.dashboard.model.AuditEntry;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.ReleaseApp;
import com.phytion.dashboard.model.ReleaseVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the release logic with a fake registry, fake code and CI services and a fake audit log: no Spring.
 */
class RegistryReleaseServiceTests {

    private static final String APP = "registry:sample-app";
    private static final String REPO = "bitbucket:andy-test-app/sample-app";
    private static final String COMMIT = "a4dad009a1f3ef48f7239a3dd155c64ca51d466f";
    private static final String COMMIT_URL = "https://bitbucket.org/andy-test-app/sample-app/commits/" + COMMIT;
    private static final CodeProperties CODE_REPOS =
            new CodeProperties(List.of(new CodeRepo("bitbucket", "andy-test-app/sample-app", null)));
    private static final CiProperties CI_PIPELINES =
            new CiProperties(List.of(new CiPipeline("jenkins", "sample-app")));
    private static final ReleaseProperties APPS = new ReleaseProperties(List.of(
            new ReleaseApp("registry", "sample-app", "jenkins:sample-app", REPO)));
    private static final Map<String, String> TAG_BUILD = Map.of("GIT_REF", "refs/tags/v1.4.0");

    private RegistryAdapter release;
    private BitbucketCodeService code;
    private JenkinsCiService ci;
    private AuditService audit;
    private RegistryReleaseService releaseService;

    @BeforeEach
    void setUp() {
        release = mock(RegistryAdapter.class);
        code = mock(BitbucketCodeService.class);
        ci = mock(JenkinsCiService.class);
        audit = mock(AuditService.class);
        when(code.commitUrl(REPO, COMMIT)).thenReturn(COMMIT_URL);
        releaseService = service(APPS, 20);
    }

    @Test
    void releaseTagsTheCommitStartsTheTagBuildAndRecordsIt() {
        when(ci.startBuild("jenkins:sample-app", TAG_BUILD)).thenReturn(Optional.of("12"));

        assertThat(releaseService.release(APP, "v1.4.0", COMMIT, "andy")).contains("12");

        verify(code).createTag(REPO, "v1.4.0", COMMIT);
        verify(audit).add("andy", AuditEntry.ActionType.RELEASE, APP, AuditEntry.ActionResult.SUCCESS, "12",
                "v1.4.0 on commit a4dad00");
    }

    @Test
    void releaseWhoseBuildIsStillQueuedIsRecordedWithoutBuildId() {
        when(ci.startBuild("jenkins:sample-app", TAG_BUILD)).thenReturn(Optional.empty());

        assertThat(releaseService.release(APP, "v1.4.0", COMMIT, "andy")).isEmpty();

        verify(audit).add("andy", AuditEntry.ActionType.RELEASE, APP, AuditEntry.ActionResult.SUCCESS, null,
                "v1.4.0 on commit a4dad00; build queued, but the CI tool hadn't started it yet");
    }

    @Test
    void tagRefusedIsRecordedAndNoBuildStarts() {
        doThrow(new CodeToolException("Bitbucket refused the tag: Tag \"v1.4.0\" already exists"))
                .when(code).createTag(REPO, "v1.4.0", COMMIT);

        assertThatThrownBy(() -> releaseService.release(APP, "v1.4.0", COMMIT, "andy"))
                .isInstanceOf(CodeToolException.class)
                .hasMessage("Bitbucket refused the tag: Tag \"v1.4.0\" already exists");

        verify(audit).add("andy", AuditEntry.ActionType.RELEASE, APP, AuditEntry.ActionResult.FAILED, null,
                "v1.4.0 on commit a4dad00: Bitbucket refused the tag: Tag \"v1.4.0\" already exists");
        verifyNoInteractions(ci);
    }

    @Test
    void buildThatDoesNotStartIsRecordedAndSaysTheTagExists() {
        when(ci.startBuild("jenkins:sample-app", TAG_BUILD)).thenThrow(new CiToolException("Jenkins not reachable"));

        assertThatThrownBy(() -> releaseService.release(APP, "v1.4.0", COMMIT, "andy"))
                .isInstanceOf(CiToolException.class)
                .hasMessage("Tag v1.4.0 was created, but its build didn't start: Jenkins not reachable");

        verify(audit).add("andy", AuditEntry.ActionType.RELEASE, APP, AuditEntry.ActionResult.FAILED, null,
                "v1.4.0 on commit a4dad00: Tag v1.4.0 was created, but its build didn't start: Jenkins not reachable");
    }

    @Test
    void versionNotLikeV140IsRejectedBeforeAnythingHappens() {
        for (String version : new String[]{"1.4.0", "v1.4", "v1.4.0-rc1", "V1.4.0", " v1.4.0", ""}) {
            assertThatThrownBy(() -> releaseService.release(APP, version, COMMIT, "andy"))
                    .isInstanceOf(InvalidReleaseException.class)
                    .hasMessage("A version looks like v1.4.0, got: " + version);
        }
        assertThatThrownBy(() -> releaseService.release(APP, null, COMMIT, "andy"))
                .isInstanceOf(InvalidReleaseException.class);
        verifyNoInteractions(code, ci, audit);
    }

    @Test
    void commitThatIsNotAFullHashIsRejectedBeforeAnythingHappens() {
        for (String commit : new String[]{"a4dad00", COMMIT.toUpperCase(), COMMIT + "0", "main"}) {
            assertThatThrownBy(() -> releaseService.release(APP, "v1.4.0", commit, "andy"))
                    .isInstanceOf(InvalidReleaseException.class)
                    .hasMessage("A commit is given by its full 40-character hash, got: " + commit);
        }
        assertThatThrownBy(() -> releaseService.release(APP, "v1.4.0", null, "andy"))
                .isInstanceOf(InvalidReleaseException.class);
        verifyNoInteractions(code, ci, audit);
    }

    @Test
    void releaseOfUnknownOrMissingAppIsRejected() {
        assertThatThrownBy(() -> releaseService.release("registry:other-app", "v1.4.0", COMMIT, "andy"))
                .isInstanceOf(AppNotFoundException.class);
        assertThatThrownBy(() -> releaseService.release(null, "v1.4.0", COMMIT, "andy"))
                .isInstanceOf(InvalidReleaseException.class)
                .hasMessage("No app given");
        verifyNoInteractions(code, ci, audit);
    }

    @Test
    void listVersionsReadsBuildCommitAndReleaseTimeFromTheLabels() {
        // The labels the Release stage of the Jenkinsfile sets
        when(release.listTags("sample-app")).thenReturn(List.of("0.1.1"));
        when(release.getLabels("sample-app", "0.1.1")).thenReturn(Map.of(
                "org.opencontainers.image.created", "2026-11-06T18:16:33Z",
                "org.opencontainers.image.revision", COMMIT,
                "org.opencontainers.image.version", "0.1.1",
                "com.phytionassetmanagement.build-number", "11"));

        assertThat(releaseService.listVersions(APP)).containsExactly(
                new ReleaseVersion("0.1.1", "11", COMMIT, COMMIT_URL, Instant.parse("2026-11-06T18:16:33Z")));
    }

    @Test
    void imageWithoutTheLabelsHasOnlyItsVersion() {
        when(release.listTags("sample-app")).thenReturn(List.of("0.1.0"));
        when(release.getLabels("sample-app", "0.1.0")).thenReturn(Map.of());

        assertThat(releaseService.listVersions(APP))
                .containsExactly(new ReleaseVersion("0.1.0", null, null, null, null));
        verify(code, never()).commitUrl(anyString(), anyString());
    }

    @Test
    void releaseTimeThatIsNotADateIsLeftOut() {
        when(release.listTags("sample-app")).thenReturn(List.of("0.1.1"));
        when(release.getLabels("sample-app", "0.1.1")).thenReturn(Map.of(
                "org.opencontainers.image.created", "yesterday",
                "com.phytionassetmanagement.build-number", "11"));

        assertThat(releaseService.listVersions(APP))
                .containsExactly(new ReleaseVersion("0.1.1", "11", null, null, null));
    }

    @Test
    void listVersionsPutsTheHighestVersionFirstByNumberNotByName() {
        // By name, "1.10.0" would come before "1.9.0", and "10.0.0" before "2.0.0"
        when(release.listTags("sample-app"))
                .thenReturn(List.of("1.9.0", "2.0.0", "1.10.0", "10.0.0", "1.9.10", "1.9.2"));
        when(release.getLabels(eq("sample-app"), anyString())).thenReturn(Map.of());

        assertThat(releaseService.listVersions(APP)).extracting(ReleaseVersion::version)
                .containsExactly("10.0.0", "2.0.0", "1.10.0", "1.9.10", "1.9.2", "1.9.0");
    }

    @Test
    void tagsInAnotherFormatComeLastByName() {
        when(release.listTags("sample-app")).thenReturn(List.of("latest", "1.4.0", "8-05663bb"));
        when(release.getLabels(eq("sample-app"), anyString())).thenReturn(Map.of());

        assertThat(releaseService.listVersions(APP)).extracting(ReleaseVersion::version)
                .containsExactly("1.4.0", "8-05663bb", "latest");
    }

    @Test
    void listVersionsOnlyReadsTheLabelsOfTheNewestMaxVersions() {
        releaseService = service(APPS, 2);
        when(release.listTags("sample-app")).thenReturn(List.of("0.1.0", "0.3.0", "0.2.0"));
        when(release.getLabels(eq("sample-app"), anyString())).thenReturn(Map.of());

        assertThat(releaseService.listVersions(APP)).extracting(ReleaseVersion::version)
                .containsExactly("0.3.0", "0.2.0");
        verify(release, never()).getLabels("sample-app", "0.1.0");
    }

    @Test
    void unknownAppIsRejected() {
        assertThatThrownBy(() -> releaseService.listVersions("registry:other-app"))
                .isInstanceOf(AppNotFoundException.class)
                .hasMessage("Unknown app: registry:other-app");
    }

    @Test
    void appOnAToolWithoutAdapterFailsAtStartup() {
        ReleaseProperties apps = new ReleaseProperties(List.of(
                new ReleaseApp("ecr", "sample-app", "jenkins:sample-app", REPO)));

        assertThatThrownBy(() -> service(apps, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ecr");
    }

    @Test
    void appWithARepoThatIsNotConfiguredFailsAtStartup() {
        ReleaseProperties apps = new ReleaseProperties(List.of(
                new ReleaseApp("registry", "sample-app", "jenkins:sample-app", "bitbucket:andy-test-app/other-app")));

        assertThatThrownBy(() -> service(apps, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bitbucket:andy-test-app/other-app");
    }

    @Test
    void appWithAPipelineThatIsNotConfiguredFailsAtStartup() {
        ReleaseProperties apps = new ReleaseProperties(List.of(
                new ReleaseApp("registry", "sample-app", "jenkins:other-app", REPO)));

        assertThatThrownBy(() -> service(apps, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenkins:other-app");
    }

    private RegistryReleaseService service(ReleaseProperties apps, int maxVersions) {
        return new RegistryReleaseService(release, code, ci, audit, apps, CODE_REPOS, CI_PIPELINES,
                new RegistryProperties("http://registry", maxVersions));
    }
}
