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

    /**
     * @param needsAdmin whether only an admin can deploy, promote or roll back there
     */
    public record EnvironmentResponse(String name, boolean needsConfirmation, boolean needsAdmin) {

        static EnvironmentResponse from(String name, EnvironmentsProperties environments) {
            return new EnvironmentResponse(name, environments.needsConfirmation(name), environments.needsAdmin(name));
        }

        static List<EnvironmentResponse> listFrom(EnvironmentsProperties environments) {
            return environments.environments().stream().map(name -> from(name, environments)).toList();
        }
    }
}
