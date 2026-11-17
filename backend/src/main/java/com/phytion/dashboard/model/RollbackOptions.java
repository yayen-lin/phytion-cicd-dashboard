package com.phytion.dashboard.model;

import java.util.List;

/**
 * The versions a rollback in an environment can go to, from the version running there.
 *
 * @param appId       the app rolled back
 * @param environment where it's rolled back
 * @param fromVersion the version running now: the newest successful deploy there, or null when nothing deployed
 *                    successfully there
 * @param toVersions  the versions deployed successfully there that are lower than fromVersion, highest first; the
 *                    first is the previous good version. Empty when there's nothing to roll back to
 */
public record RollbackOptions(String appId, String environment, String fromVersion, List<String> toVersions) {
}
