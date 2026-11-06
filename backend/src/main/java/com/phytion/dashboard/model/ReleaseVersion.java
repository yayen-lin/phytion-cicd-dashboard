package com.phytion.dashboard.model;

import java.time.Instant;

/**
 * One released version of an app: an image in the release tool. Everything but the version comes from the labels the
 * Release stage of the app's Jenkinsfile puts on the image, so each is null when the image doesn't have that label.
 *
 * @param version   the image's tag, e.g. "1.4.0" (released from the git tag v1.4.0)
 * @param buildId   the CI build that made it, e.g. "12"
 * @param commit    the full hash of the commit it was built from
 * @param commitUrl that commit's page in the code tool (null when the commit is)
 * @param createdAt when it was released (the CI build's time)
 */
public record ReleaseVersion(
        String version,
        String buildId,
        String commit,
        String commitUrl,
        Instant createdAt) {
}
