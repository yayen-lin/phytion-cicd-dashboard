package com.phytion.dashboard.model;

/**
 * One branch of a repo
 *
 * @param name         e.g. "main"
 * @param latestCommit the commit the branch points to
 * @param url          the branch's page in Bitbucket
 */
public record CodeBranch(
        String name,
        CodeCommit latestCommit,
        String url) {
}
