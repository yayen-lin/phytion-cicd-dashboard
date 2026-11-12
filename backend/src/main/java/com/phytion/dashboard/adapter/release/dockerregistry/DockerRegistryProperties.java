package com.phytion.dashboard.adapter.release.dockerregistry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Docker registry settings.
 *
 * @param url         the registry's address
 * @param maxVersions how many versions of an app the Release page shows, newest first
 */
@ConfigurationProperties("dashboard.docker-registry")
public record DockerRegistryProperties(String url, int maxVersions) {
}
