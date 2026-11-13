package com.phytion.dashboard.adapter.operate.kubernetes;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Kubernetes settings. Each environment is a namespace with the same name.
 *
 * @param url    the cluster's API server, e.g. "https://127.0.0.1:6443"
 * @param token  the dashboard-reader service account's token
 * @param caData the cluster's CA certificate, base64 PEM as in a kubeconfig's certificate-authority-data; blank to
 *               trust only the JVM's default CAs
 */
@ConfigurationProperties("dashboard.kubernetes")
public record KubernetesProperties(String url, String token, String caData) {
}
