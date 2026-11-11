package com.phytion.dashboard.service;

import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.config.CodeProperties;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Branches and commits of the configured code repos, and creating tags in them. A tag isn't recorded in the audit log
 * here: the action that creates it (a release) records itself.
 */
@Service
@RequiredArgsConstructor
public class BitbucketCodeService {

    private final BitbucketAdapter code;
    private final CodeProperties properties;

    /**
     * Return the configured repos in config order.
     */
    public List<CodeRepo> listRepos() {
        return properties.repos();
    }

    /**
     * Lists out the branches of this repoId.
     *
     * @param repoId repo identifier
     * @throws RepoNotFoundException when no configured repo identifier has that id
     */
    public List<CodeBranch> listBranches(String repoId) {
        CodeRepo repo = repo(repoId);
        return code.listBranches(repo.workspace(), repo.name());
    }

    /**
     * Lists out the commits of this branch under this repoId.
     *
     * @param repoId repo identifier
     * @throws RepoNotFoundException when no configured repo has that id
     */
    public List<CodeCommit> listCommits(String repoId, String branch) {
        CodeRepo repo = repo(repoId);
        return code.listCommits(repo.workspace(), repo.name(), branch);
    }

    /**
     * The address of a commit's page in the code tool.
     *
     * @param repoId     repo identifier
     * @param commitHash full commit hash
     * @throws RepoNotFoundException when no configured repo has that id
     */
    public String getCommitUrl(String repoId, String commitHash) {
        CodeRepo repo = repo(repoId);
        return code.getCommitUrl(repo.workspace(), repo.name(), commitHash);
    }

    /**
     * Creates a tag on a commit, with the repo's token.
     *
     * @param repoId     repo identifier
     * @param tag        version tag
     * @param commitHash full commit hash
     * @throws RepoNotFoundException when no configured repo identifier has that id
     * @throws CodeToolException     when the repo identifier has no token, or request getting refused
     */
    public void createTag(String repoId, String tag, String commitHash) {
        CodeRepo repo = repo(repoId);
        if (!repo.hasToken()) {
            throw new CodeToolException("No token is set for " + repo.id()
                    + ", so the dashboard can't create tags there");
        }
        code.createTag(repo.workspace(), repo.name(), repo.token(), tag, commitHash);
    }

    private CodeRepo repo(String repoId) {
        return properties.repos().stream()
                .filter(repo -> repo.id().equals(repoId))
                .findFirst()
                .orElseThrow(() -> new RepoNotFoundException(repoId));
    }
}
