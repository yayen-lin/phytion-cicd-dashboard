package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.config.EnvironmentsProperties;
import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.AppSummary;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.DeployJob;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.model.ReleaseVersion;
import com.phytion.dashboard.service.AppService;
import com.phytion.dashboard.service.AppSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;
    private final AppSummaryService appSummaryService;
    private final EnvironmentsProperties environmentsProperties;

    // UNUSED
    @GetMapping("/api/apps")
    public List<AppResponse> listApps() {
        return appService.listApps().stream().map(AppResponse::new).toList();
    }

    /**
     * One app's repo, pipeline, release and deploy
     */
    @GetMapping("/api/apps/{appId}")
    public AppResponse getApp(@PathVariable String appId) {
        return new AppResponse(appService.getApp(appId));
    }

    /**
     * Everything the Apps page shows: the environments, and each app's latest build, latest version and
     * what runs in each environment
     */
    @GetMapping("/api/apps/summary")
    public AppsSummaryResponse listAppSummaries() {
        return new AppsSummaryResponse(
                EnvironmentController.EnvironmentResponse.listFrom(environmentsProperties),
                appSummaryService.listAppSummaries().stream().map(AppSummaryResponse::new).toList());
    }

    /**
     * @param id      the name of the app
     * @param release null when the app isn't released
     * @param deploy  null when the app isn't deployed
     */
    public record AppResponse(String id, RepoResponse repo, CiPipeline pipeline, ReleaseImage release,
                              DeployJob deploy) {
        AppResponse(App app) {
            this(app.id(), new RepoResponse(app.repo().tool(), app.repo().workspace(), app.repo().name()),
                    app.pipeline(), app.release(), app.deploy());
        }
    }

    public record RepoResponse(String tool, String workspace, String name) {
    }

    /**
     * @param environments the configured environments, in order
     * @param apps         one summary per app, in the config's order
     */
    public record AppsSummaryResponse(List<EnvironmentController.EnvironmentResponse> environments,
                                      List<AppSummaryResponse> apps) {
    }

    /**
     * An app's summary, with the app as /api/apps shows it (no tokens)
     */
    public record AppSummaryResponse(AppResponse app, CiBuild latestBuild, ReleaseVersion latestVersion,
                                     List<AppSummary.Deployment> deployments, List<AppSummary.Part> unavailable) {
        AppSummaryResponse(AppSummary summary) {
            this(new AppResponse(summary.app()), summary.latestBuild(), summary.latestVersion(),
                    summary.deployments(), summary.unavailable());
        }
    }
}
