package com.phytion.dashboard.model;

/**
 * One build with the details only the single-build view shows.
 *
 * @param build     the build, as in the builds list
 * @param startedBy who started the build: the dashboard user when it was started through the dashboard, otherwise
 *                  the CI tool's user; null when no user started it (e.g. a push or a timer)
 */
public record CiBuildDetails(CiBuild build, String startedBy) {
}
