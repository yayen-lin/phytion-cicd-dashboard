package com.phytion.dashboard.adapter.ci.jenkins;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jenkins settings
 *
 * @param url               where Jenkins runs
 * @param user              the Jenkins user the dashboard logs in as
 * @param token             that user's Jenkins API token
 * @param maxBuilds         how many recent builds listBuilds returns
 * @param queuePollInterval how often triggerBuild asks Jenkins whether the queued build has started
 * @param queueTimeout      how long triggerBuild waits for the queued build to start before giving up on its number
 */
@ConfigurationProperties("dashboard.jenkins")
public record JenkinsProperties(
        String url,
        String user,
        String token,
        int maxBuilds,
        Duration queuePollInterval,
        Duration queueTimeout) {
}
