package com.phytion.dashboard.model;

/**
 * An app running in an environment, as the operate tool sees it (in Kubernetes, a Deployment).
 *
 * @param name        the name of the workload (app id)
 * @param version     the version it runs, or null when it can't be told
 * @param desiredPods how many pods it should have
 * @param readyPods   how many of them are ready to take traffic
 */
public record OperateWorkload(String name, String version, int desiredPods, int readyPods) {
}
