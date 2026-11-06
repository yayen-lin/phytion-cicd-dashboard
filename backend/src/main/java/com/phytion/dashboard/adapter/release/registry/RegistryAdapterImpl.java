package com.phytion.dashboard.adapter.release.registry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.phytion.dashboard.adapter.release.ReleaseToolException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Release adapter for a Docker registry
 */
@Component
public class RegistryAdapterImpl implements RegistryAdapter {

    // Without this Accept header the registry answers with an old manifest format, or refuses
    private static final String MANIFEST_TYPES = String.join(", ",
            "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json");

    // The registry sends blobs as application/octet-stream, which RestClient won't read as JSON: they are parsed here
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient registry;

    @Autowired
    public RegistryAdapterImpl(RegistryProperties properties) {
        this(RestClient.builder().baseUrl(properties.url()).build());
    }

    // For tests, which pass a RestClient wired to a fake registry
    RegistryAdapterImpl(RestClient registry) {
        this.registry = registry;
    }

    @Override
    public List<String> listTags(String image) {
        return call(() -> {
            try {
                RegistryJson.Tags response = registry.get()
                        .uri("/v2/{image}/tags/list", image)
                        .retrieve()
                        .body(RegistryJson.Tags.class);
                return response == null || response.tags() == null ? List.of() : response.tags();
            } catch (HttpClientErrorException.NotFound e) {
                // Nothing pushed under that name yet
                return List.of();
            }
        });
    }

    @Override
    public Map<String, String> getLabels(String image, String tag) {
        return call(() -> {
            try {
                RegistryJson.Manifest manifest = manifest(image, tag);
                if (manifest != null && manifest.config() == null && manifest.manifests() != null) {
                    // An index: follow it to the image (the entry with a real platform, not an attestation)
                    Optional<String> digest = manifest.manifests().stream()
                            .filter(entry -> entry.platform() == null || !"unknown".equals(entry.platform().os()))
                            .map(RegistryJson.IndexEntry::digest)
                            .findFirst();
                    if (digest.isEmpty()) {
                        return Map.of();
                    }
                    manifest = manifest(image, digest.get());
                }
                if (manifest == null || manifest.config() == null) {
                    return Map.of();
                }
                String config = registry.get()
                        .uri("/v2/{image}/blobs/{digest}", image, manifest.config().digest())
                        .retrieve()
                        .body(String.class);
                RegistryJson.ImageSettings settings = config == null ? null
                        : JSON.readValue(config, RegistryJson.Config.class).config();
                return settings == null || settings.labels() == null ? Map.of() : settings.labels();
            } catch (HttpClientErrorException.NotFound e) {
                return Map.of();
            }
        });
    }

    /** @param reference a tag, or a digest ("sha256:...") */
    private RegistryJson.Manifest manifest(String image, String reference) {
        return registry.get()
                .uri("/v2/{image}/manifests/{reference}", image, reference)
                .header("Accept", MANIFEST_TYPES)
                .retrieve()
                .body(RegistryJson.Manifest.class);
    }

    /**
     * Runs a request to the registry, turning HTTP client errors into a ReleaseToolException with a short message.
     */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            throw new ReleaseToolException("Registry answered " + e.getStatusCode().value() + " " + e.getStatusText(), e);
        } catch (ResourceAccessException e) {
            throw new ReleaseToolException("Registry not reachable", e);
        } catch (RestClientException | JacksonException e) {
            throw new ReleaseToolException("Registry request failed", e);
        }
    }
}
