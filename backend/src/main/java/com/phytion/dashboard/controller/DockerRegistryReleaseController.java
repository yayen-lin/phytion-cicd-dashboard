package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeBranchCommits;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.ReleaseDraft;
import com.phytion.dashboard.model.ReleaseVersion;
import com.phytion.dashboard.service.DockerRegistryReleaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The released versions of each app, and releasing a new version
 */
@RestController
@RequiredArgsConstructor
public class DockerRegistryReleaseController {

    private final DockerRegistryReleaseService releaseService;

    /**
     * @param appId the name of the app
     */
    @GetMapping("/api/release/versions")
    public VersionsResponse listVersions(@RequestParam String appId) {
        return new VersionsResponse(appId, releaseService.listVersions(appId));
    }

    /**
     * What the Release dialog starts with.
     */
    @GetMapping("/api/release/draft")
    public ReleaseDraftResponse draftRelease(@RequestParam String appId,
                                             @RequestParam(required = false) String branch,
                                             @RequestParam(defaultValue = "true") boolean withCommits) {
        ReleaseDraft draft = releaseService.draftRelease(appId, branch, withCommits);
        CodeBranchCommits commits = draft.commits();
        return new ReleaseDraftResponse(appId, draft.nextVersion(),
                commits == null ? null : commits.branches(),
                commits == null ? null : commits.branch(),
                commits == null ? null : commits.commits());
    }

    /**
     * Tags a commit as a new version and starts its build; the version shows up once the build has pushed it
     */
    @PostMapping("/api/release/versions")
    public ReleaseResponse release(@RequestBody @Valid ReleaseRequest request, Authentication authentication) {
        return new ReleaseResponse(request.appId(), request.version(), releaseService.release(request.appId(),
                request.version(), request.commit(), authentication.getName()).orElse(null));
    }

    public record VersionsResponse(String app, List<ReleaseVersion> versions) {
    }

    /**
     * @param nextVersion the version to suggest
     * @param branches    every branch, or null when withCommits was false
     * @param branch      the branch the commits are from (null when not asked, or the repo has no branches)
     * @param commits     that branch's recent commits, or null when withCommits was false
     */
    public record ReleaseDraftResponse(String app, String nextVersion, List<CodeBranch> branches, String branch,
                                       List<CodeCommit> commits) {
    }

    /**
     * @param appId   the name of the app
     * @param version the git tag to create, e.g. "v1.4.0"
     * @param commit  the full hash of the commit to be tagged and release
     */
    public record ReleaseRequest(@NotBlank String appId, @NotBlank String version, @NotBlank String commit) {
    }

    /**
     * @param buildId the tag build's id in the app's CI pipeline, or null when the CI tool hasn't started it yet
     */
    public record ReleaseResponse(String app, String version, String buildId) {
    }
}
