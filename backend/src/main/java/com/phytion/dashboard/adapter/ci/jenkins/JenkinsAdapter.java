package com.phytion.dashboard.adapter.ci.jenkins;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.CiBuildDetails;
import com.phytion.dashboard.model.CiBuild;

/**
 * jenkins ci implementation
 */
public interface JenkinsAdapter {

    /** most recent builds of a job, newest first. */
    List<CiBuild> listBuilds(String job);

    /** One build of a job with who started it in Jenkins (null if no user did), or empty if it doesn't exist. */
    Optional<CiBuildDetails> getBuild(String job, String buildId);

    /**
     * Starts a new build of a job. Returns the new build's id, or empty when the tool hasn't started the build yet
     */
    Optional<String> triggerBuild(String job);
}
