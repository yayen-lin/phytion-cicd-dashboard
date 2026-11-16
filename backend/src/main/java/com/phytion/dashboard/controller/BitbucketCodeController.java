package com.phytion.dashboard.controller;

import java.util.List;

import com.phytion.dashboard.model.CodeBranch;
import com.phytion.dashboard.model.CodeBranchCommits;
import com.phytion.dashboard.model.CodeCommit;
import com.phytion.dashboard.service.BitbucketCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The branches and commits of each app's repo
 */
@RestController
@RequiredArgsConstructor
public class BitbucketCodeController {

    private final BitbucketCodeService codeService;

    @GetMapping("/api/code/branches")
    public BranchesResponse listBranches(@RequestParam String appId) {
        return new BranchesResponse(appId, codeService.listBranches(appId));
    }

    @GetMapping("/api/code/commits")
    public CommitsResponse listCommits(@RequestParam String appId, @RequestParam(required = false) String branch) {
        CodeBranchCommits result = codeService.listBranchCommits(appId, branch);
        return new CommitsResponse(appId, result.branches(), result.branch(), result.commits());
    }

    public record BranchesResponse(String app, List<CodeBranch> branches) {
    }

    public record CommitsResponse(String app, List<CodeBranch> branches, String branch, List<CodeCommit> commits) {
    }
}
