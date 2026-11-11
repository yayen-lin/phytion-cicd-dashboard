package com.phytion.dashboard.model;

/**
 * A CI pipeline the dashboard shows, and the external tool that runs and hosts it.
 *
 * @param tool which CI tool runs the pipeline
 * @param job  the job's name in that tool
 */
public record CiPipeline(String tool, String job) {
    public String id() {
        return tool + ":" + job;
    }
}
