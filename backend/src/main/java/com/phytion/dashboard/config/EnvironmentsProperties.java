package com.phytion.dashboard.config;

import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dashboard")
public record EnvironmentsProperties(@NotEmpty List<String> environments, List<String> confirmEnvironments,
                                     List<String> adminEnvironments) {

    public EnvironmentsProperties {
        confirmEnvironments = confirmEnvironments == null ? List.of() : confirmEnvironments;
        adminEnvironments = adminEnvironments == null ? List.of() : adminEnvironments;
    }

    public boolean contains(String environment) {
        return environment != null && environments.contains(environment);
    }

    public boolean needsConfirmation(String environment) {
        return confirmEnvironments.contains(environment);
    }

    public boolean needsAdmin(String environment) {
        return adminEnvironments.contains(environment);
    }

    /**
     * Returns the next environment in the config, or empty for the last one or one
     */
    public Optional<String> nextEnvironment(String environment) {
        int index = environments.indexOf(environment);
        if (index < 0 || index == environments.size() - 1) {
            return Optional.empty();
        }
        return Optional.of(environments.get(index + 1));
    }
}
