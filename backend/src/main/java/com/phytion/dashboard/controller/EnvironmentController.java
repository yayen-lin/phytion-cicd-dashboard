package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.config.EnvironmentsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The configured environments, read from the config
 */
@RestController
@RequiredArgsConstructor
public class EnvironmentController {

    private final EnvironmentsProperties environments;

    @GetMapping("/api/environments")
    public List<EnvironmentResponse> listEnvironments() {
        return EnvironmentResponse.listFrom(environments);
    }

    public record EnvironmentResponse(String name, boolean needsConfirmation) {
        static List<EnvironmentResponse> listFrom(EnvironmentsProperties environments) {
            return environments.environments().stream()
                    .map(name -> new EnvironmentResponse(name, environments.needsConfirmation(name)))
                    .toList();
        }
    }
}
