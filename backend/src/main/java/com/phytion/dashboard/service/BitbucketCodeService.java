package com.phytion.dashboard.service;

import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Branches and commits of the apps' repos, and creating tags in them.
 */
@Service
@RequiredArgsConstructor
public class BitbucketCodeService {

    private final BitbucketAdapter code;
    private final AppService apps;

    /**
     * Lists out the branches of an app's repo.
     *
     * @param appId name of the app
     * @throws AppNotFoundException when no configured app has that id
     */
    public List<CodeBranch> listBranches(String appId) {
        CodeRepo repo = repo(appId);
        return code.listBranches(repo.workspace(), repo.name());
    }

    /**
     * Lists out the commits on a branch of an app's repo.
     *
     * @param appId name of the app
     * @throws AppNotFoundException when no configured app has that id
     */
    public List<CodeCommit> listCommits(String appId, String branch) {
        CodeRepo repo = repo(appId);
        return code.listCommits(repo.workspace(), repo.name(), branch);
    }

    /**
     * The address of a commit's page.
     *
     * @param appId      name of the app
     * @param commitHash full commit hash
     * @throws AppNotFoundException when no configured app has that id
     */
    public String getCommitUrl(String appId, String commitHash) {
        CodeRepo repo = repo(appId);
        return code.getCommitUrl(repo.workspace(), repo.name(), commitHash);
    }

    /**
     * Creates a tag on a commit.
     *
     * @param appId      name of the app
     * @param tag        version tag
     * @param commitHash full commit hash
     * @throws AppNotFoundException when no configured app has that id
     * @throws CodeToolException    when the repo has no token, or the code tool refuses
     */
    public void createTag(String appId, String tag, String commitHash) {
        CodeRepo repo = repo(appId);
        if (!repo.hasToken()) {
            throw new CodeToolException("No token is set for " + appId + "'s repo (" + repo.workspace() + "/"
                    + repo.name() + "), so the dashboard can't create tags there");
        }
        code.createTag(repo.workspace(), repo.name(), repo.token(), tag, commitHash);
    }

    private CodeRepo repo(String appId) {
        return apps.getApp(appId).repo();
    }
}
