package com.phytion.dashboard.model;

/**
 * One run of an app's deploy job, started from the dashboard or straight in the CI tool.
 *
 * @param appId       the app deployed
 * @param version     the version it deployed
 * @param environment where it deployed it
 * @param build       the deploy build, with its status and when it started
 * @param deployedBy  the dashboard user when started from the dashboard, else the CI tool's user (null when no user
 *                    started it)
 */
public record DeployRun(String appId, String version, String environment, CiBuild build, String deployedBy) {
}
