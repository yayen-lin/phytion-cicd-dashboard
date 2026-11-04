package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.JenkinsCiBuildEntry;
import com.phytion.dashboard.service.JenkinsCiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CI pipelines and their builds
 */
@RestController
@RequiredArgsConstructor
public class JenkinsCiController {

    private final JenkinsCiService ciService;

    @GetMapping("/api/ci/pipelines")
    public List<PipelineResponse> listPipelines() {
        return ciService.pipelines().stream().map(PipelineResponse::new).toList();
    }

    @GetMapping("/api/ci/builds")
    public BuildsResponse listBuilds(@RequestParam String pipeline) {
        return new BuildsResponse(pipeline, ciService.listBuilds(pipeline));
    }

    @PostMapping("/api/ci/builds")
    public TriggerResponse triggerBuild(@RequestParam String pipeline, Authentication authentication) {
        return new TriggerResponse(ciService.triggerBuild(pipeline, authentication.getName()).orElse(null));
    }

    /** @param id e.g. "jenkins:sample-app" */
    public record PipelineResponse(String id, String tool, String job) {
        PipelineResponse(CiPipeline pipeline) {
            this(pipeline.id(), pipeline.tool(), pipeline.job());
        }
    }

    public record BuildsResponse(String pipeline, List<JenkinsCiBuildEntry> builds) {
    }

    /** @param buildId the started build's id, or null when the CI tool hasn't started it yet */
    public record TriggerResponse(String buildId) {
    }
}
