package com.phytion.dashboard.model;

/**
 * One build with the details only the single-build view shows.
 *
 * @param build           the build, as in the builds list
 * @param startedBy       who started the build
 * @param startedBySource where the {@code startedBy} name comes from
 * @param testReport      every test's result, or null when the build has none (still running, or it stopped before the
 *                        tests)
 */
public record CiBuildDetails(CiBuild build, String startedBy, String startedBySource, CiTestReport testReport) {
    public static final String DASHBOARD = "dashboard";
}
