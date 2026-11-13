package com.phytion.dashboard.service;

public class AppNotFoundException extends RuntimeException {

    public AppNotFoundException(String appId) {
        super("Unknown app: " + appId);
    }

    private AppNotFoundException(String appId, String part) {
        super("App " + appId + " has no " + part + " configured");
    }

    /** @param appId an app that is configured, but isn't released */
    public static AppNotFoundException noRelease(String appId) {
        return new AppNotFoundException(appId, "release");
    }

    /** @param appId an app that is configured, but isn't deployed */
    public static AppNotFoundException noDeploy(String appId) {
        return new AppNotFoundException(appId, "deploy");
    }
}
