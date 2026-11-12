package com.phytion.dashboard.controller;

import java.util.List;

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
