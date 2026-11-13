package com.phytion.dashboard.config;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dashboard")
public record EnvironmentsProperties(@NotEmpty List<String> environments) {
    public boolean contains(String environment) {
        return environment != null && environments.contains(environment);
    }
}
