package com.phytion.dashboard.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.phytion.dashboard.model.App;
import com.phytion.dashboard.model.CiPipeline;
import com.phytion.dashboard.model.CodeRepo;
import com.phytion.dashboard.model.ReleaseImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("dashboard")
public record AppsProperties(Map<String, @Valid AppSettings> apps) {

    public AppsProperties {
        apps = apps == null ? Map.of() : new LinkedHashMap<>(apps);
    }

    public List<App> list() {
        return apps.entrySet().stream()
                .map(e -> new App(
                        e.getKey(),
                        e.getValue().repo(),
                        e.getValue().pipeline(),
                        e.getValue().release()))
                .toList();
    }

    /**
     * An app's settings.
     *
     * @param release optional
     */
    public record AppSettings(
            @NotNull @Valid CodeRepo repo,
            @NotNull @Valid CiPipeline pipeline,
            @Valid ReleaseImage release) {
    }
}
