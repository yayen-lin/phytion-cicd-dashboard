package com.phytion.dashboard.model;

/**
 * A code repo the dashboard shows, and the tool that hosts it. Configured in application.properties.
 *
 * @param tool which code tool hosts the repo, e.g. "bitbucket"
 * @param repo the repo's name in that tool, e.g. "andy-test-app/sample-app" (workspace/repo)
 */
public record CodeRepo(String tool, String repo) {

    /** e.g. "bitbucket:andy-test-app/sample-app": how the API refers to the repo */
    public String id() {
        return tool + ":" + repo;
    }
}
