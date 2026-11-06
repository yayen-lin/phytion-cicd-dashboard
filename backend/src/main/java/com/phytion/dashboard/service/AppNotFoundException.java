package com.phytion.dashboard.service;

/**
 * The API was asked about a release app that isn't in the configured list.
 */
public class AppNotFoundException extends RuntimeException {

    public AppNotFoundException(String appId) {
        super("Unknown app: " + appId);
    }
}
