package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.ReleaseApp;
import com.phytion.dashboard.model.ReleaseVersion;
import com.phytion.dashboard.service.RegistryReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Released apps and their versions, and releasing a new version
 */
@RestController
@RequiredArgsConstructor
public class RegistryReleaseController {

    private final RegistryReleaseService releaseService;

    @GetMapping("/api/release/apps")
    public List<AppResponse> listApps() {
        return releaseService.apps().stream().map(AppResponse::new).toList();
    }

    /** @param app e.g. "registry:sample-app" */
    @GetMapping("/api/release/versions")
    public VersionsResponse listVersions(@RequestParam String app) {
        return new VersionsResponse(app, releaseService.listVersions(app));
    }

    /** Tags a commit as a new version and starts its build; the version shows up once the build has pushed it */
    @PostMapping("/api/release/versions")
    public ReleaseResponse release(@RequestBody ReleaseRequest request, Authentication authentication) {
        return new ReleaseResponse(request.app(), request.version(), releaseService.release(request.app(),
                request.version(), request.commit(), authentication.getName()).orElse(null));
    }

    /**
     * @param id       e.g. "registry:sample-app"
     * @param pipeline the CI pipeline that releases it, e.g. "jenkins:sample-app"
     * @param repo     the code repo that releases are tagged in, e.g. "bitbucket:andy-test-app/sample-app"
     */
    public record AppResponse(String id, String tool, String image, String pipeline, String repo) {
        AppResponse(ReleaseApp app) {
            this(app.id(), app.tool(), app.image(), app.pipeline(), app.repo());
        }
    }

    public record VersionsResponse(String app, List<ReleaseVersion> versions) {
    }

    /**
     * @param app     e.g. "registry:sample-app"
     * @param version the git tag to create, e.g. "v1.4.0"
     * @param commit  the full hash of the commit to release
     */
    public record ReleaseRequest(String app, String version, String commit) {
    }

    /** @param buildId the tag build's id in the app's CI pipeline, or null when the CI tool hasn't started it yet */
    public record ReleaseResponse(String app, String version, String buildId) {
    }
}
