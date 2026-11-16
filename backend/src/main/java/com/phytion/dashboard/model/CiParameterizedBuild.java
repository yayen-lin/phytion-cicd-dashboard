package com.phytion.dashboard.model;

import java.util.Map;

/**
 * One build of a job with the parameters it ran with, e.g. a deploy build's VERSION and ENVIRONMENT.
 *
 * @param build      the build, as in the builds list
 * @param parameters the build's parameters as text, e.g. {VERSION=1.0.1, ENVIRONMENT=staging}
 * @param startedBy  the CI tool's user who started it, or null when no user did
 */
public record CiParameterizedBuild(CiBuild build, Map<String, String> parameters, String startedBy) {
}
