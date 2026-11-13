package com.phytion.dashboard.adapter.operate.kubernetes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.phytion.dashboard.adapter.operate.OperateToolException;
import com.phytion.dashboard.model.OperateWorkload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Tests the Kubernetes adapter against a fake API server, using JSON shaped like real responses.
 */
class KubernetesAdapterImplTests {

    private static final String STAGING_DEPLOYMENTS =
            "http://kubernetes/apis/apps/v1/namespaces/staging/deployments?labelSelector=app.kubernetes.io%2Fmanaged-by%3DHelm";

    private MockRestServiceServer kubernetes;
    private KubernetesAdapterImpl adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kubernetes");
        kubernetes = MockRestServiceServer.bindTo(builder).build();
        adapter = new KubernetesAdapterImpl(builder.build());
    }

    @Test
    void listWorkloadsReadsVersionAndPodsOfEachHelmDeployment() {
        kubernetes.expect(requestTo(STAGING_DEPLOYMENTS))
                .andRespond(withSuccess("""
                        {"kind": "DeploymentList", "apiVersion": "apps/v1", "items": [
                          {"metadata": {"name": "sample-app", "namespace": "staging",
                             "labels": {"app.kubernetes.io/managed-by": "Helm", "app.kubernetes.io/name": "sample-app",
                                        "app.kubernetes.io/version": "1.0.1"}},
                           "spec": {"replicas": 2, "template": {"spec": {"containers": [
                             {"name": "sample-app", "image": "localhost:5001/sample-app:1.0.1"}]}}},
                           "status": {"replicas": 2, "readyReplicas": 1, "availableReplicas": 1}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.listWorkloads("staging")).containsExactly(new OperateWorkload("sample-app", "1.0.1", 2, 1));
    }

    @Test
    void workloadWithoutVersionLabelTakesTheImageTagAndNoReadyCountMeansNoneReady() {
        // the API server leaves out readyReplicas when no pod is ready
        kubernetes.expect(requestTo(STAGING_DEPLOYMENTS))
                .andRespond(withSuccess("""
                        {"items": [
                          {"metadata": {"name": "dummy-app"},
                           "spec": {"replicas": 1, "template": {"spec": {"containers": [
                             {"name": "dummy-app", "image": "localhost:5001/dummy-app:2.0.0"}]}}},
                           "status": {"replicas": 1, "unavailableReplicas": 1}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(adapter.listWorkloads("staging")).containsExactly(new OperateWorkload("dummy-app", "2.0.0", 1, 0));
    }

    @Test
    void emptyNamespaceHasNoWorkloads() {
        kubernetes.expect(requestTo(STAGING_DEPLOYMENTS))
                .andRespond(withSuccess("{\"kind\": \"DeploymentList\", \"items\": []}", MediaType.APPLICATION_JSON));

        assertThat(adapter.listWorkloads("staging")).isEmpty();
    }

    @Test
    void missingNamespaceHasNoWorkloads() {
        kubernetes.expect(requestTo(STAGING_DEPLOYMENTS)).andRespond(withResourceNotFound());

        assertThat(adapter.listWorkloads("staging")).isEmpty();
    }

    @Test
    void forbiddenNamespaceBecomesOperateToolExceptionWithJustTheStatus() {
        kubernetes.expect(requestTo(STAGING_DEPLOYMENTS)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter.listWorkloads("staging"))
                .isInstanceOf(OperateToolException.class)
                .hasMessage("Kubernetes answered 403 Forbidden");
    }

    @Test
    void imageTagIsTheTextAfterTheLastColonOfTheName() {
        assertThat(KubernetesAdapterImpl.toImageTag("localhost:5001/sample-app:1.0.1")).isEqualTo("1.0.1");
        assertThat(KubernetesAdapterImpl.toImageTag("sample-app:1.0.1")).isEqualTo("1.0.1");
        assertThat(KubernetesAdapterImpl.toImageTag("localhost:5001/sample-app")).isNull();
        assertThat(KubernetesAdapterImpl.toImageTag("sample-app@sha256:abc")).isNull();
        assertThat(KubernetesAdapterImpl.toImageTag(null)).isNull();
    }
}
