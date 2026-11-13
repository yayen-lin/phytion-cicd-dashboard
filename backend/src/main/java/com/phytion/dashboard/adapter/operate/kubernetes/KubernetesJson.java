package com.phytion.dashboard.adapter.operate.kubernetes;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The API server's JSON responses that KubernetesAdapter reads. Everything else is ignored.
 */
final class KubernetesJson {

    private KubernetesJson() {
    }

    /** GET /apis/apps/v1/namespaces/{namespace}/deployments */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeploymentList(List<Deployment> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Deployment(Metadata metadata, DeploymentSpec spec, DeploymentStatus status) {
    }

    /** "labels" is null when the object has none */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metadata(String name, Map<String, String> labels) {
    }

    /** "replicas" is the desired pod count */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeploymentSpec(Integer replicas, PodTemplate template) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PodTemplate(PodSpec spec) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PodSpec(List<Container> containers) {
    }

    /** "image" e.g. "localhost:5001/sample-app:1.0.1" */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Container(String name, String image) {
    }

    /** "readyReplicas" is left out (null) when no pod is ready */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeploymentStatus(Integer readyReplicas) {
    }
}
