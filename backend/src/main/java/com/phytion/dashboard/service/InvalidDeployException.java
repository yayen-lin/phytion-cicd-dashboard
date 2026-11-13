package com.phytion.dashboard.service;

/**
 * A deploy was asked for with a version that isn't released, or an environment that doesn't exist.
 */
public class InvalidDeployException extends RuntimeException {

    public InvalidDeployException(String message) {
        super(message);
    }
}
