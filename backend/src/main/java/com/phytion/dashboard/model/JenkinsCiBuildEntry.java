package com.phytion.dashboard.model;

import java.time.Instant;

/**
 * One build of a CI job
 *
 * @param id             jenkins build number
 * @param status         where the build is at
 * @param commit         the git commit the build built, or null
 * @param branch         the branch the build built, or null
 * @param startedAt      when the build started
 * @param durationMillis how long the build took, or null while it is still running
 * @param url            the build's page in the CI tool
 */
public record JenkinsCiBuildEntry(
        String id,
        Status status,
        String commit,
        String branch,
        Instant startedAt,
        Long durationMillis,
        String url) {

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED,
        UNSTABLE,
        ABORTED,
        UNKNOWN
    }
}
