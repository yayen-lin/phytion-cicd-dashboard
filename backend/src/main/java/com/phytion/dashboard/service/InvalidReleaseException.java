package com.phytion.dashboard.service;

/**
 * A release was asked for with a version or a commit that isn't in the right format.
 */
public class InvalidReleaseException extends RuntimeException {

    public InvalidReleaseException(String message) {
        super(message);
    }
}
