package com.phytion.dashboard.model;

import jakarta.validation.constraints.NotBlank;

/**
 * An app's CI pipeline, and the external tool that runs and hosts it.
 *
 * @param tool which CI tool runs the pipeline, e.g. "jenkins"
 * @param job  the job's name in that tool, e.g. "sample-app"
 */
public record CiPipeline(@NotBlank String tool, @NotBlank String job) {
}
