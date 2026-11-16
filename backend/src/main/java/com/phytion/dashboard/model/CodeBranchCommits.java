package com.phytion.dashboard.model;

import java.util.List;

/**
 * A repo's branches, with the commits on one of them.
 *
 * @param branches every branch, the most recently committed to first
 * @param branch   the branch the commits are from, or null when the repo has no branches
 * @param commits  the most recent commits on that branch, newest first
 */
public record CodeBranchCommits(List<CodeBranch> branches, String branch, List<CodeCommit> commits) {
}
