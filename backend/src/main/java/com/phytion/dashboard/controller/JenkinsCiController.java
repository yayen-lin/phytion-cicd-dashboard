package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.JenkinsCiBuildEntry;
import com.phytion.dashboard.service.JenkinsCiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * jenkins ci builds api
 */
@RestController
@RequiredArgsConstructor
public class JenkinsCiController {

    private final JenkinsCiService ciService;

    @GetMapping("/api/ci/builds")
    public BuildsResponse listBuilds() {
        return new BuildsResponse(ciService.job(), ciService.listBuilds());
    }

    @PostMapping("/api/ci/builds")
    public TriggerResponse triggerBuild(Authentication authentication) {
        return new TriggerResponse(ciService.triggerBuild(authentication.getName()).orElse(null));
    }

    public record BuildsResponse(String job, List<JenkinsCiBuildEntry> builds) {
    }

    /** @param buildId the started build's id, or null when the CI tool hasn't started it yet */
    public record TriggerResponse(String buildId) {
    }
}
