package com.phytion.dashboard.model;

import jakarta.validation.constraints.NotBlank;

/**
 * An app's CI pipeline, and the external tool that runs and hosts it.
 *
 * @param tool the external ci tool that runs the pipeline
 * @param job  the name of the job in that tool
 */
public record CiPipeline(@NotBlank String tool, @NotBlank String job) {
}
