package com.phytion.dashboard.model;

import java.util.List;

/**
 * The recent actions taken through the dashboard, each with the CI build it started.
 *
 * @param entries     the audit log entries, newest first
 * @param unavailable the parts whose tool couldn't be reached
 */
public record History(List<Entry> entries, List<Part> unavailable) {

    public enum Part {
        BUILDS // from the CI tool
    }

    /**
     * One action and its build.
     *
     * @param entry the audit log entry
     * @param build the build it started, from the app's pipeline (a build or release) or its deploy job (a deploy);
     *              null when it started none, the build is older than the recent builds, or the CI tool is down
     */
    public record Entry(AuditEntry entry, CiBuild build) {
    }
}
