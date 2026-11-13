package com.phytion.dashboard.adapter.operate.kubernetes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.model.OperateWorkload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Operate adapter for Kubernetes
 */
@Component
public class KubernetesAdapterImpl implements KubernetesAdapter {

    // Helm puts these labels on what it installs (the chart's Deployment template sets the version one)
    private static final String HELM_INSTALLED = "app.kubernetes.io/managed-by=Helm";
    private static final String LABEL_VERSION = "app.kubernetes.io/version";

    private final RestClient kubernetes;

    @Autowired
    public KubernetesAdapterImpl(KubernetesProperties properties) {
        this(RestClient.builder()
                .baseUrl(properties.url())
                .requestFactory(new JdkClientHttpRequestFactory(httpClientTrusting(properties.caData())))
                .defaultHeaders(headers -> headers.setBearerAuth(properties.token()))
                .build());
    }

    // constructor for tests, which pass a RestClient wired to a fake API server
    KubernetesAdapterImpl(RestClient kubernetes) {
        this.kubernetes = kubernetes;
    }

    @Override
    public List<OperateWorkload> listWorkloads(String namespace) {
        return call(() -> {
            try {
                KubernetesJson.DeploymentList response = kubernetes.get()
                        .uri("/apis/apps/v1/namespaces/{namespace}/deployments?labelSelector={selector}",
                                namespace, HELM_INSTALLED)
                        .retrieve()
                        .body(KubernetesJson.DeploymentList.class);
                if (response == null || response.items() == null) {
                    return List.of();
                }
                return response.items().stream().map(KubernetesAdapterImpl::toWorkload).toList();
            } catch (HttpClientErrorException.NotFound e) {
                return List.of();
            }
        });
    }

    /**
     * Turns a Deployment into a workload; a missing replica count means 1 (Kubernetes' default), a missing ready count
     * means none are ready.
     */
    private static OperateWorkload toWorkload(KubernetesJson.Deployment deployment) {

        // version from the label, else from the first container's image tag
        Map<String, String> labels = deployment.metadata().labels() == null ? Map.of() : deployment.metadata().labels();
        String version = labels.get(LABEL_VERSION);
        if (version == null) {
            version = toImageTag(firstImage(deployment));
        }

        // pod counts
        int desiredPods = deployment.spec() == null || deployment.spec().replicas() == null
                ? 1
                : deployment.spec().replicas();
        int readyPods = deployment.status() == null || deployment.status().readyReplicas() == null
                ? 0
                : deployment.status().readyReplicas();

        return new OperateWorkload(deployment.metadata().name(), version, desiredPods, readyPods);
    }

    /** The image of the Deployment's first container, or null when it has none */
    private static String firstImage(KubernetesJson.Deployment deployment) {
        if (deployment.spec() == null || deployment.spec().template() == null
                || deployment.spec().template().spec() == null
                || deployment.spec().template().spec().containers() == null
                || deployment.spec().template().spec().containers().isEmpty()) {
            return null;
        }
        return deployment.spec().template().spec().containers().getFirst().image();
    }

    /**
     * Convert an image's tag version tag, e.g. "localhost:5001/sample-app:1.0.1" -> "1.0.1"
     * Returns null for null, an image without a tag ("sample-app") or one pinned by digest ("sample-app@sha256:...").
     */
    static String toImageTag(String image) {
        if (image == null || image.contains("@")) {
            return null;
        }
        int colon = image.lastIndexOf(':');
        return colon > image.lastIndexOf('/') ? image.substring(colon + 1) : null;
    }

    /**
     * An HTTP client that trusts the cluster's CA (a base64 PEM, as in a kubeconfig), or the JVM's default CAs when
     * none is given.
     * Throws IllegalStateException when the CA isn't a valid certificate, so the app doesn't start with a bad one.
     */
    private static HttpClient httpClientTrusting(String caData) {
        if (caData == null || caData.isBlank()) {
            return HttpClient.newHttpClient();
        }
        try {

            // put the CA certificates in a trust store
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            int index = 0;
            for (Certificate certificate : CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(Base64.getDecoder().decode(caData.trim())))) {
                trustStore.setCertificateEntry("kubernetes-ca-" + index++, certificate);
            }

            // build a TLS context trusting only that store
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(trustStore);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustManagers.getTrustManagers(), null);

            return HttpClient.newBuilder().sslContext(ssl).build();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new IllegalStateException("dashboard.kubernetes.ca-data isn't a valid base64 PEM certificate", e);
        }
    }

    /**
     * Runs a request to the API server, turning HTTP client errors into an OperateToolException with a short message.
     */
    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            throw new OperateToolException("Kubernetes answered " + e.getStatusCode().value() + " " + e.getStatusText(), e);
        } catch (ResourceAccessException e) {
            throw new OperateToolException("Kubernetes not reachable", e);
        } catch (RestClientException e) {
            throw new OperateToolException("Kubernetes request failed", e);
        }
    }
}
