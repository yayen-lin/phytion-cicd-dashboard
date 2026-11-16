package com.phytion.dashboard.model;

/**
 * What the Release dialog starts with.
 *
 * @param nextVersion the version to suggest: the highest vX.Y.Z tag with the last number bumped, e.g. "v1.0.3"
 * @param commits     the branches with one branch's commits to pick from, or null when the commit is already picked
 */
public record ReleaseDraft(String nextVersion, CodeBranchCommits commits) {
}
