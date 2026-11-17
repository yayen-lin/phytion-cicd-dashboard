package com.phytion.dashboard.model;

/**
 * A deploy of an older version to an environment
 *
 * @param appId       the app rolled back
 * @param environment where it's rolled back
 * @param fromVersion the version that ran there: the newest successful deploy
 * @param toVersion   the version the deploy job deploys
 * @param buildId     the deploy build's id, or null when the CI tool hasn't started it yet
 */
public record Rollback(String appId, String environment, String fromVersion, String toVersion, String buildId) {
}
