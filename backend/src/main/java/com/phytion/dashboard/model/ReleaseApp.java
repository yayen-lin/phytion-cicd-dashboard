package com.phytion.dashboard.model;

/**
 * An app the Release page shows: its image in a release tool, the CI pipeline that releases it, and the repo its code
 * is in. Configured in application.properties.
 *
 * @param tool     which release tool stores the images, e.g. "registry"
 * @param image    the image's name in that tool, e.g. "sample-app"
 * @param pipeline the id of the CI pipeline that builds and pushes the image, e.g. "jenkins:sample-app"
 * @param repo     the id of the code repo that releases are tagged in, e.g. "bitbucket:andy-test-app/sample-app"
 */
public record ReleaseApp(String tool, String image, String pipeline, String repo) {

    /** e.g. "registry:sample-app": how the API refers to the app */
    public String id() {
        return tool + ":" + image;
    }
}
