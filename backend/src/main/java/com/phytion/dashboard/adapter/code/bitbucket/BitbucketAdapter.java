package com.phytion.dashboard.adapter.code.bitbucket;

import java.util.List;

import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;

/**
 * bitbucket code implementation
 */
public interface BitbucketAdapter {

    /**
     * Branches of a repo, the most recently committed to first.
     *
     * @param repo e.g. "andy-test-app/sample-app" (workspace/repo)
     */
    List<CodeBranch> listBranches(String repo);

    /**
     * Most recent commits on a branch, newest first.
     *
     * @param repo e.g. "andy-test-app/sample-app" (workspace/repo)
     */
    List<CodeCommit> listCommits(String repo, String branch);
}
