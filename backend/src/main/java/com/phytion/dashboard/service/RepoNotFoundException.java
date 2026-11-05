package com.phytion.dashboard.service;

/**
 * The API was asked about a code repo that isn't in the configured list.
 */
public class RepoNotFoundException extends RuntimeException {

    public RepoNotFoundException(String repoId) {
        super("Unknown repo: " + repoId);
    }
}
