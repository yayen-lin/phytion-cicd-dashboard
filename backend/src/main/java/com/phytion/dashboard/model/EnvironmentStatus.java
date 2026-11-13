package com.phytion.dashboard.model;

import java.util.List;

/**
 * An environment and every deployable app in it, deployed or not.
 *
 * @param environment the env to look for
 * @param apps        one entry per app, in the config's order
 */
public record EnvironmentStatus(String environment, List<EnvironmentApp> apps) {
}
