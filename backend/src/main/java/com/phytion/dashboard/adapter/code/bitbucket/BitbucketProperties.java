package com.phytion.dashboard.adapter.code.bitbucket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bitbucket settings.
 *
 * @param url         Bitbucket's REST API
 * @param webUrl      Bitbucket's web link
 * @param maxBranches how many branches listBranches returns (Bitbucket allows at most 100)
 * @param maxCommits  how many recent commits listCommits returns (Bitbucket allows at most 100)
 */
@ConfigurationProperties("dashboard.bitbucket")
public record BitbucketProperties(
        String url,
        String webUrl,
        int maxBranches,
        int maxCommits) {
}
