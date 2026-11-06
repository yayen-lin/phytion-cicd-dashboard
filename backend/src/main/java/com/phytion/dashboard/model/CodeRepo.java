package com.phytion.dashboard.model;

/**
 * A code repo the dashboard shows, and the tool that hosts it. Configured in application.properties.
 *
 * @param tool  which code tool hosts the repo, e.g. "bitbucket"
 * @param repo  the repo's name in that tool, e.g. "andy-test-app/sample-app" (workspace/repo)
 * @param token an access token for this repo with write access, used to create tags (releases); null or blank when
 *              none is set. Reading the repo doesn't use it: the repos are public
 */
public record CodeRepo(String tool, String repo, String token) {

    /** e.g. "bitbucket:andy-test-app/sample-app": how the API refers to the repo */
    public String id() {
        return tool + ":" + repo;
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /** Without the token itself, so it can't end up in a log */
    @Override
    public String toString() {
        return "CodeRepo[tool=" + tool + ", repo=" + repo + ", token=" + (hasToken() ? "(set)" : "(none)") + "]";
    }
}
