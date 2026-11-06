package com.phytion.dashboard.service;

import java.util.List;

import com.phytion.dashboard.adapter.code.CodeToolException;
import com.phytion.dashboard.adapter.code.bitbucket.BitbucketAdapter;
import com.phytion.dashboard.config.CodeProperties;
import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import org.springframework.stereotype.Service;

/**
 * Branches and commits of the configured code repos, and creating tags in them. A tag isn't recorded in the audit log
 * here: the action that creates it (a release) records itself.
 */
@Service
public class BitbucketCodeService {

    private static final String BITBUCKET = "bitbucket";

    private final BitbucketAdapter code;
    private final List<CodeRepo> repos;

    public BitbucketCodeService(BitbucketAdapter code, CodeProperties properties) {
        // Fail at startup rather than on the first request
        for (CodeRepo repo : properties.repos()) {
            // Bitbucket is the only code tool with an adapter so far
            if (!BITBUCKET.equals(repo.tool())) {
                throw new IllegalStateException("No adapter for code tool '" + repo.tool()
                        + "' (repo " + repo.id() + "); only 'bitbucket' is supported");
            }
            if (repo.repo() == null || !repo.repo().matches("[^/]+/[^/]+")) {
                throw new IllegalStateException("Repo '" + repo.repo() + "' must be workspace/repo, "
                        + "e.g. andy-test-app/sample-app");
            }
        }
        this.code = code;
        this.repos = properties.repos();
    }

    /** The configured repos, in config order. */
    public List<CodeRepo> repos() {
        return repos;
    }

    /**
     * @param repoId e.g. "bitbucket:andy-test-app/sample-app"
     * @throws RepoNotFoundException when no configured repo has that id
     */
    public List<CodeBranch> listBranches(String repoId) {
        return code.listBranches(repo(repoId).repo());
    }

    /**
     * @param repoId e.g. "bitbucket:andy-test-app/sample-app"
     * @throws RepoNotFoundException when no configured repo has that id
     */
    public List<CodeCommit> listCommits(String repoId, String branch) {
        return code.listCommits(repo(repoId).repo(), branch);
    }

    /**
     * The address of a commit's page in the code tool.
     *
     * @param repoId e.g. "bitbucket:andy-test-app/sample-app"
     * @param hash   the commit's full hash
     * @throws RepoNotFoundException when no configured repo has that id
     */
    public String commitUrl(String repoId, String hash) {
        return code.commitUrl(repo(repoId).repo(), hash);
    }

    /**
     * Creates a tag on a commit, with the repo's token.
     *
     * @param repoId e.g. "bitbucket:andy-test-app/sample-app"
     * @param name   the tag, e.g. "v1.4.0"
     * @param hash   the commit's full hash
     * @throws RepoNotFoundException when no configured repo has that id
     * @throws CodeToolException     when the repo has no token, or the code tool refuses (e.g. the tag already exists)
     */
    public void createTag(String repoId, String name, String hash) {
        CodeRepo repo = repo(repoId);
        if (!repo.hasToken()) {
            throw new CodeToolException("No token is set for " + repo.id()
                    + ", so the dashboard can't create tags there");
        }
        code.createTag(repo.repo(), repo.token(), name, hash);
    }

    private CodeRepo repo(String repoId) {
        return repos.stream()
                .filter(repo -> repo.id().equals(repoId))
                .findFirst()
                .orElseThrow(() -> new RepoNotFoundException(repoId));
    }
}
