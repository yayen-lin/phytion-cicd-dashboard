package com.phytion.dashboard.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings the frontend reads from the backend.
 *
 * @param pollInterval how often a page or dialog with a running build or deploy loads again, e.g. 3s
 */
@Validated
@ConfigurationProperties("dashboard.frontend")
public record FrontendProperties(@NotNull Duration pollInterval) {
}
