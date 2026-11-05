package com.phytion.dashboard.model;

import java.util.List;

/**
 * All test results of one build
 *
 * @param counts         how many passed, failed and were skipped
 * @param durationMillis how long the tests took together
 * @param cases          every test, in the order the CI tool lists them
 */
public record CiTestReport(CiTestCounts counts, long durationMillis, List<CiTestCase> cases) {
}
