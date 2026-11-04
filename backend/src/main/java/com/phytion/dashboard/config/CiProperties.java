package com.phytion.dashboard.config;

import java.util.List;

import com.phytion.dashboard.model.CiPipeline;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CI settings
 *
 * @param pipelines the CI jobs the dashboard shows, in the order the pipeline selector lists them
 */
@ConfigurationProperties("dashboard.ci")
public record CiProperties(List<CiPipeline> pipelines) {

    public CiProperties {
        pipelines = pipelines == null ? List.of() : List.copyOf(pipelines);
    }
}
