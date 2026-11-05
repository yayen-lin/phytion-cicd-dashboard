package com.phytion.dashboard.model;

/**
 * One test of a build
 *
 * @param className      the test class, e.g. "com.phytion.dummyapp.CreditCalculatorTests"
 * @param name           the test, e.g. "noMethaneIsNoCredits()"
 * @param status         how it went
 * @param durationMillis how long it took
 * @param errorMessage   why it failed (the message, or the stack trace when there is no message), or null when it
 *                       didn't
 */
public record CiTestCase(
        String className,
        String name,
        Status status,
        long durationMillis,
        String errorMessage) {

    public enum Status {
        PASSED,
        FAILED,
        SKIPPED
    }
}
