package com.phytion.dashboard.adapter.ci.jenkins;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Jenkins' JSON API responses that JenkinsAdapter reads. Everything else is ignored.
 */
final class JenkinsJson {

    private JenkinsJson() {
    }

    /** GET /job/{job}/api/json */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(List<Build> builds) {
    }

    // jenkin's build fields
    static final String BUILD_FIELDS =
            "number,result,inProgress,timestamp,duration,url,"
                    + "actions[lastBuiltRevision[SHA1,branch[name]],totalCount,failCount,skipCount]";

    // BUILD_FIELDS plus what started the build, for the single-build view
    static final String BUILD_DETAIL_FIELDS =
            "number,result,inProgress,timestamp,duration,url,"
                    + "actions[lastBuiltRevision[SHA1,branch[name]],totalCount,failCount,skipCount,causes[userId,userName]]";

    // A build's test report, for the test results view
    static final String TEST_REPORT_FIELDS =
            "duration,passCount,failCount,skipCount,suites[cases[className,name,status,duration,errorDetails,errorStackTrace]]";

    /** GET /job/{job}/{number}/api/json, and each entry of Job.builds */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Build(
            long number,
            String result,
            boolean inProgress,
            long timestamp,
            long duration,
            String url,
            List<Action> actions) {
    }

    /**
     * Most actions come back empty; the git plugin's holds the built commit, the CauseAction what started the build,
     * the TestResultAction the test counts (totalCount includes the failed and skipped ones)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Action(Revision lastBuiltRevision, List<Cause> causes, Integer totalCount, Integer failCount,
                  Integer skipCount) {
    }

    /** GET /job/{job}/{number}/testReport/api/json. Durations are in seconds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestReport(double duration, int passCount, int failCount, int skipCount, List<TestSuite> suites) {
    }

    /** One test class's results */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestSuite(List<TestCase> cases) {
    }

    /**
     * status: PASSED, SKIPPED, FAILED, or compared with the build before: FIXED (passes again), REGRESSION (newly
     * fails). errorDetails is the failure message (null when the failure has none, e.g. a bare assertTrue),
     * errorStackTrace where it failed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TestCase(String className, String name, String status, double duration, String errorDetails,
                    String errorStackTrace) {
    }

    /** Why a build started. userId/userName are set only when a Jenkins user started it (not for a push or a timer). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cause(String userId, String userName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Revision(@JsonProperty("SHA1") String sha1, List<Branch> branch) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Branch(String name) {
    }

    /** GET /queue/item/{id}/api/json: a queued build. "executable" is set once a worker has started it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QueueItem(boolean cancelled, Executable executable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Executable(long number) {
    }
}
