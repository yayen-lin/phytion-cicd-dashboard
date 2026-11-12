package com.phytion.dashboard.adapter.release.dockerregistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import com.phytion.dashboard.adapter.release.ReleaseToolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the registry adapter against a fake registry, using JSON shaped like real registry:2 responses.
 */
class DockerRegistryAdapterImplTests {

    private static final MediaType INDEX = MediaType.parseMediaType("application/vnd.oci.image.index.v1+json");
    private static final MediaType MANIFEST = MediaType.parseMediaType("application/vnd.oci.image.manifest.v1+json");

    // Digests go in the path with ":" encoded (sha256%3A...), which the registry accepts
    private MockRestServiceServer registry;
    private DockerRegistryAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://registry");
        registry = MockRestServiceServer.bindTo(builder).build();
        adapter = new DockerRegistryAdapterImpl(builder.build());
    }

    @Test
    void listTagsReturnsTheTags() {
        registry.expect(requestTo("http://registry/v2/sample-app/tags/list"))
                .andRespond(withSuccess("{\"name\": \"sample-app\", \"tags\": [\"0.1.0\", \"0.1.1\"]}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.listTags("sample-app")).containsExactly("0.1.0", "0.1.1");
    }

    @Test
    void imageNeverPushedHasNoTags() {
        registry.expect(requestTo("http://registry/v2/new-app/tags/list")).andRespond(withResourceNotFound());

        assertThat(adapter.listTags("new-app")).isEmpty();
    }

    @Test
    void imageWithEveryTagDeletedHasNoTags() {
        registry.expect(requestTo("http://registry/v2/sample-app/tags/list"))
                .andRespond(withSuccess("{\"name\": \"sample-app\", \"tags\": null}", MediaType.APPLICATION_JSON));

        assertThat(adapter.listTags("sample-app")).isEmpty();
    }

    @Test
    void getLabelsFollowsAnIndexToTheImageNotTheAttestation() {
        // What Docker Desktop pushes: an index with the image and an attestation entry
        registry.expect(requestTo("http://registry/v2/sample-app/manifests/1.4.0"))
                .andExpect(header("Accept", org.hamcrest.Matchers.containsString("application/vnd.oci.image.index.v1+json")))
                .andRespond(withSuccess("""
                        {"schemaVersion": 2, "mediaType": "application/vnd.oci.image.index.v1+json",
                         "manifests": [
                           {"mediaType": "application/vnd.oci.image.manifest.v1+json", "digest": "sha256:image",
                            "platform": {"architecture": "arm64", "os": "linux"}},
                           {"mediaType": "application/vnd.oci.image.manifest.v1+json", "digest": "sha256:attestation",
                            "annotations": {"vnd.docker.reference.type": "attestation-manifest"},
                            "platform": {"architecture": "unknown", "os": "unknown"}}
                         ]}
                        """, INDEX));
        registry.expect(requestTo("http://registry/v2/sample-app/manifests/sha256%3Aimage"))
                .andRespond(withSuccess("""
                        {"schemaVersion": 2, "mediaType": "application/vnd.oci.image.manifest.v1+json",
                         "config": {"mediaType": "application/vnd.oci.image.config.v1+json", "digest": "sha256:config"},
                         "layers": []}
                        """, MANIFEST));
        // The registry sends blobs as application/octet-stream
        registry.expect(requestTo("http://registry/v2/sample-app/blobs/sha256%3Aconfig"))
                .andRespond(withSuccess("""
                        {"architecture": "arm64", "created": "2026-11-05T22:53:57.575236837Z",
                         "config": {"User": "app", "Labels": {
                           "org.opencontainers.image.created": "2026-11-06T18:16:33Z",
                           "org.opencontainers.image.revision": "a4dad009a1f3ef48f7239a3dd155c64ca51d466f"}}}
                        """, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(adapter.getLabels("sample-app", "1.4.0")).isEqualTo(Map.of(
                "org.opencontainers.image.created", "2026-11-06T18:16:33Z",
                "org.opencontainers.image.revision", "a4dad009a1f3ef48f7239a3dd155c64ca51d466f"));
        registry.verify();
    }

    @Test
    void getLabelsReadsASingleImageDirectly() {
        registry.expect(requestTo("http://registry/v2/sample-app/manifests/1.4.0"))
                .andRespond(withSuccess("""
                        {"schemaVersion": 2, "mediaType": "application/vnd.oci.image.manifest.v1+json",
                         "config": {"digest": "sha256:config"}, "layers": []}
                        """, MANIFEST));
        registry.expect(requestTo("http://registry/v2/sample-app/blobs/sha256%3Aconfig"))
                .andRespond(withSuccess("{\"config\": {\"Labels\": {\"org.opencontainers.image.version\": \"1.4.0\"}}}",
                        MediaType.APPLICATION_OCTET_STREAM));

        assertThat(adapter.getLabels("sample-app", "1.4.0")).containsExactly(entry("org.opencontainers.image.version", "1.4.0"));
        registry.verify();
    }

    @Test
    void imageWithoutLabelsHasNone() {
        // Docker writes "Labels": null when the image was built without any
        registry.expect(requestTo("http://registry/v2/sample-app/manifests/1.4.0"))
                .andRespond(withSuccess("""
                        {"schemaVersion": 2, "mediaType": "application/vnd.oci.image.manifest.v1+json",
                         "config": {"digest": "sha256:config"}, "layers": []}
                        """, MANIFEST));
        registry.expect(requestTo("http://registry/v2/sample-app/blobs/sha256%3Aconfig"))
                .andRespond(withSuccess("{\"config\": {\"Labels\": null}}", MediaType.APPLICATION_OCTET_STREAM));

        assertThat(adapter.getLabels("sample-app", "1.4.0")).isEmpty();
    }

    @Test
    void unknownTagHasNoLabels() {
        registry.expect(requestTo("http://registry/v2/sample-app/manifests/9.9.9")).andRespond(withResourceNotFound());

        assertThat(adapter.getLabels("sample-app", "9.9.9")).isEmpty();
    }

    @Test
    void registryErrorBecomesReleaseToolExceptionWithJustTheStatus() {
        registry.expect(requestTo("http://registry/v2/sample-app/tags/list")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.listTags("sample-app"))
                .isInstanceOf(ReleaseToolException.class)
                .hasMessage("Registry answered 500 Internal Server Error");
    }
}
