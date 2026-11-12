package com.phytion.dashboard.model;

/**
 * An app the dashboard shows: its code repo, the CI pipeline that builds it, and where its released images go.
 *
 * @param id       the name of the app in the config
 * @param repo     the repo of the app
 * @param pipeline the CI pipeline that builds, tests and releases the app
 * @param release  where its released images go, or null when the app isn't released
 */
public record App(String id, CodeRepo repo, CiPipeline pipeline, ReleaseImage release) {
}
