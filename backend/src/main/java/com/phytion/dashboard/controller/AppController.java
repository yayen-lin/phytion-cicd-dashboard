package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.ReleaseImage;
import com.phytion.dashboard.service.AppService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AppController {

    private final AppService appService;

    @GetMapping("/api/apps")
    public List<AppResponse> listApps() {
        return appService.listApps().stream().map(AppResponse::new).toList();
    }

    /**
     * @param id      the name of the app
     * @param release null when the app isn't released
     */
    public record AppResponse(String id, RepoResponse repo, CiPipeline pipeline, ReleaseImage release) {
        AppResponse(App app) {
            this(app.id(), new RepoResponse(app.repo().tool(), app.repo().workspace(), app.repo().name()),
                    app.pipeline(), app.release());
        }
    }

    public record RepoResponse(String tool, String workspace, String name) {
    }
}
