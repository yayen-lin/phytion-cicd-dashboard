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
        return codeService.listRepos().stream().map(RepoResponse::new).toList();
    }

    /**
     * @param repoId repo identifier
     */
    @GetMapping("/api/code/branches")
    public BranchesResponse listBranches(@RequestParam String repoId) {
        return new BranchesResponse(repoId, codeService.listBranches(repoId));
    }

    /**
     * @param repoId repo identifier
     */
    @GetMapping("/api/code/commits")
    public CommitsResponse listCommits(@RequestParam String repoId, @RequestParam String branch) {
        return new CommitsResponse(repoId, branch, codeService.listCommits(repoId, branch));
    }

    public record RepoResponse(String id, String tool, String workspace, String name) {
        RepoResponse(CodeRepo repo) {
            this(repo.id(), repo.tool(), repo.workspace(), repo.name());
        }
    }

    public record BranchesResponse(String repo, List<CodeBranch> branches) {
    }

    public record CommitsResponse(String repo, String branch, List<CodeCommit> commits) {
    }
}
