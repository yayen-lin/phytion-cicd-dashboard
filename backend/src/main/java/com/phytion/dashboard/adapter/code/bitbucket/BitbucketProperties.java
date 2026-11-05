package com.phytion.dashboard.adapter.code.bitbucket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bitbucket settings. No credentials yet: the repos are public, so the dashboard reads them anonymously.
 *
 * @param url         Bitbucket's REST API, e.g. https://api.bitbucket.org/2.0
 * @param maxBranches how many branches listBranches returns (Bitbucket allows at most 100)
 * @param maxCommits  how many recent commits listCommits returns (Bitbucket allows at most 100)
 */
@ConfigurationProperties("dashboard.bitbucket")
public record BitbucketProperties(
        String url,
        int maxBranches,
        int maxCommits) {
}
