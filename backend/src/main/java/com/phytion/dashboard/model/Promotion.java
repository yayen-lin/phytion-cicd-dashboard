package com.phytion.dashboard.model;

/**
 * A version moved from one environment to the next
 *
 * @param appId           the app promoted
 * @param version         the version that ran in the source environment
 * @param fromEnvironment the source environment
 * @param toEnvironment   the next environment, where the deploy job deploys the version
 * @param buildId         the deploy build's id, or null when the CI tool hasn't started it yet
 */
public record Promotion(String appId, String version, String fromEnvironment, String toEnvironment, String buildId) {
}
