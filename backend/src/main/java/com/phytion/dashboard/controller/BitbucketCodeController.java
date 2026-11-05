package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.service.BitbucketCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Code repos, their branches and their commits
 */
@RestController
@RequiredArgsConstructor
public class BitbucketCodeController {

    private final BitbucketCodeService codeService;

    @GetMapping("/api/code/repos")
    public List<RepoResponse> listRepos() {
        return codeService.repos().stream().map(RepoResponse::new).toList();
    }

    /** @param repo e.g. "bitbucket:andy-test-app/sample-app" */
    @GetMapping("/api/code/branches")
    public BranchesResponse listBranches(@RequestParam String repo) {
        return new BranchesResponse(repo, codeService.listBranches(repo));
    }

    /** @param repo e.g. "bitbucket:andy-test-app/sample-app" */
    @GetMapping("/api/code/commits")
    public CommitsResponse listCommits(@RequestParam String repo, @RequestParam String branch) {
        return new CommitsResponse(repo, branch, codeService.listCommits(repo, branch));
    }

    /** @param id e.g. "bitbucket:andy-test-app/sample-app" */
    public record RepoResponse(String id, String tool, String repo) {
        RepoResponse(CodeRepo repo) {
            this(repo.id(), repo.tool(), repo.repo());
        }
    }

    public record BranchesResponse(String repo, List<CodeBranch> branches) {
    }

    public record CommitsResponse(String repo, String branch, List<CodeCommit> commits) {
    }
}
