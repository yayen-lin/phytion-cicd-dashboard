package com.phytion.dashboard.adapter.ci.jenkins;

import java.util.List;
import java.util.Optional;

import com.phytion.dashboard.model.JenkinsCiBuildEntry;

/**
 * jenkins ci implementation
 */
public interface JenkinsAdapter {

    /** most recent builds of a job, newest first. */
    List<JenkinsCiBuildEntry> listBuilds(String job);

    /** One build of a job, or empty if it doesn't exist. */
    Optional<JenkinsCiBuildEntry> getBuild(String job, String buildId);

    /**
     * Starts a new build of a job. Returns the new build's id, or empty when the tool hasn't started the build yet
     */
    Optional<String> triggerBuild(String job);
}
