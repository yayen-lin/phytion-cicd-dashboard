package com.phytion.dashboard.model;

import jakarta.validation.constraints.NotBlank;

/**
 * The job that deploys an app's released versions, and the tool that runs it.
 *
 * @param tool which tool runs the deploy job
 * @param job  the name of the job in that tool
 */
public record DeployJob(@NotBlank String tool, @NotBlank String job) {
}
