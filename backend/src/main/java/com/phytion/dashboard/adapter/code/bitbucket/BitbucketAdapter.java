package com.phytion.dashboard.adapter.code.bitbucket;

import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
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

    /**
     * The address of a commit's page in Bitbucket. Built without asking Bitbucket, so it doesn't check that the commit
     * exists.
     *
     * @param repo e.g. "andy-test-app/sample-app" (workspace/repo)
     * @param hash the commit's full hash
     */
    String commitUrl(String repo, String hash);

    /**
     * Creates a tag on a commit.
     *
     * @param repo  e.g. "andy-test-app/sample-app" (workspace/repo)
     * @param token an access token for that repo that may write to it (Repositories: Write)
     * @param name  the tag, e.g. "v1.4.0"
     * @param hash  the commit's full hash
     * @throws CodeToolException with Bitbucket's reason when it refuses, e.g. the tag already exists or the token is
     *                           expired
     */
    void createTag(String repo, String token, String name, String hash);
}
