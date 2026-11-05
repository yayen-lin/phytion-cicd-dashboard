package com.phytion.dashboard.config;

import java.util.List;

import com.phytion.dashboard.model.CiPipeline;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dashboard.ci")
public record CiProperties(List<CiPipeline> pipelines) {

    public CiProperties {
        pipelines = pipelines == null ? List.of() : List.copyOf(pipelines);
    }
}
