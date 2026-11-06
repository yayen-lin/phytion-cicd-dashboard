package com.phytion.dashboard.config;

import java.util.List;

import com.phytion.dashboard.model.ReleaseApp;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dashboard.release")
public record ReleaseProperties(List<ReleaseApp> apps) {

    public ReleaseProperties {
        apps = apps == null ? List.of() : List.copyOf(apps);
    }
}
