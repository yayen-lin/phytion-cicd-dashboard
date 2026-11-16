package com.phytion.dashboard.model;

import java.util.List;

/**
 * One app at a glance: its latest build, its latest version, and what runs in each environment. A part whose tool
 * can't be reached is null (or empty) and listed in {@code unavailable}.
 *
 * @param app           the app
 * @param latestBuild   its pipeline's newest build, or null when there's none
 * @param latestVersion its highest released version, or null when there's none or the app isn't released
 * @param deployments   one entry per environment, in the config's order; empty when the app isn't deployed
 * @param unavailable   the parts whose tool couldn't be reached
 */
public record AppSummary(
        App app,
        CiBuild latestBuild,
        ReleaseVersion latestVersion,
        List<Deployment> deployments,
        List<Part> unavailable) {

    /** The parts of a summary, each read from its own tool */
    public enum Part {
        BUILD,
        VERSION,
        ENVIRONMENTS
    }

    /**
     * The app in one environment.
     *
     * @param environment the environment's name
     * @param state       what runs there; every field but appId is null when the app isn't deployed there
     */
    public record Deployment(String environment, EnvironmentApp state) {
    }
}
