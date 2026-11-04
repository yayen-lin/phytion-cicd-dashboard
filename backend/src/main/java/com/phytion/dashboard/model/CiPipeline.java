package com.phytion.dashboard.model;

/**
 * A CI job the dashboard shows, and the tool that runs it. Configured in application.properties.
 *
 * @param tool which CI tool runs the job, e.g. "jenkins"
 * @param job  the job's name in that tool, e.g. "sample-app"
 */
public record CiPipeline(String tool, String job) {

    /** e.g. "jenkins:sample-app": how the API and the audit log refer to the pipeline */
    public String id() {
        return tool + ":" + job;
    }
}
