package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.EnvironmentOverview;
import com.phytion.dashboard.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OverviewController {

    private final OverviewService overviewService;

    @GetMapping("/api/overview")
    public OverviewResponse getOverview() {
        return new OverviewResponse(overviewService.listEnvironmentOverviews());
    }

    public record OverviewResponse(List<EnvironmentOverview> environments) {
    }
}
