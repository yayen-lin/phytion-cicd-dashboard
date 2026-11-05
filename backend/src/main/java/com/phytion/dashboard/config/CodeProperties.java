package com.phytion.dashboard.config;

import java.util.List;

import com.phytion.dashboard.model.CodeRepo;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dashboard.code")
public record CodeProperties(List<CodeRepo> repos) {

    public CodeProperties {
        repos = repos == null ? List.of() : List.copyOf(repos);
    }
}
