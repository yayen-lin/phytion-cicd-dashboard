package com.phytion.dashboard.adapter.release.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Docker registry settings. No credentials yet: the local registry has no login.
 *
 * @param url         the registry's address, e.g. http://localhost:5001
 * @param maxVersions how many versions of an app the Release page shows, newest first
 */
@ConfigurationProperties("dashboard.registry")
public record RegistryProperties(
        String url,
        int maxVersions) {
}
