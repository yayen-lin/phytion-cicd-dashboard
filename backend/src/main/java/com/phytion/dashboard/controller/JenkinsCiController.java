package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiTestReport;
import com.phytion.dashboard.model.CiBuild;
import com.phytion.dashboard.service.JenkinsCiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The builds of each app's CI pipeline
 */
@RestController
@RequiredArgsConstructor
public class JenkinsCiController {

    private final JenkinsCiService ciService;

    @GetMapping("/api/ci/builds")
    public BuildsResponse listBuilds(@RequestParam String appId) {
        return new BuildsResponse(appId, ciService.listBuilds(appId));
    }

    @GetMapping("/api/ci/builds/{buildId}")
    public ResponseEntity<CiBuildDetails> getBuild(@RequestParam String appId, @PathVariable String buildId) {
        return ResponseEntity.of(ciService.getBuild(appId, buildId));
    }

    @GetMapping("/api/ci/builds/{buildId}/tests")
    public ResponseEntity<CiTestReport> getTestReport(@RequestParam String appId, @PathVariable String buildId) {
        return ResponseEntity.of(ciService.getTestReport(appId, buildId));
    }

    @PostMapping("/api/ci/builds")
    public TriggerResponse triggerBuild(@RequestParam String appId, Authentication authentication) {
        return new TriggerResponse(ciService.triggerBuild(appId, authentication.getName()).orElse(null));
    }

    public record BuildsResponse(String app, List<CiBuild> builds) {
    }

    /** @param buildId the started build's id, or null when the CI tool hasn't started it yet */
    public record TriggerResponse(String buildId) {
    }
}
