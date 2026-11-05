package com.phytion.dashboard.model;

/**
 * How a build's tests went
 *
 * @param passed  tests that passed
 * @param failed  tests that failed
 * @param skipped tests that didn't run (e.g. disabled)
 */
public record CiTestCounts(int passed, int failed, int skipped) {
}
