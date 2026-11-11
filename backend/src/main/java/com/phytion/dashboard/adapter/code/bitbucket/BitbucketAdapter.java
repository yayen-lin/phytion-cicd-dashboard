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
     * List branches of a repo
     */
    List<CodeBranch> listBranches(String workspace, String name);

    /**
     * List commits on a branch
     */
    List<CodeCommit> listCommits(String workspace, String name, String branch);

    /**
     * The url of a commit
     */
    String getCommitUrl(String workspace, String name, String hash);

    /**
     * Creates a tag on a commit.
     *
     * @throws CodeToolException with Bitbucket's reason when it refuses, e.g. the tag already exists or the token is
     *                           expired
     */
    void createTag(String workspace, String name, String token, String tag, String commitHash);
}
