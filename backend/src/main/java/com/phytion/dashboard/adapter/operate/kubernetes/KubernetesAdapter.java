package com.phytion.dashboard.adapter.operate.kubernetes;

import java.util.List;

import com.phytion.dashboard.model.OperateWorkload;

/**
 * Kubernetes implementation (the Kubernetes REST API). Read-only.
 */
public interface KubernetesAdapter {

    /**
     * The Helm-installed Deployments in a namespace.
     * e.g. "staging" -> [sample-app 1.0.1, 1 of 1 pods ready]
     *
     * @param namespace an environment's namespace, e.g. "staging"
     * @return the workloads, or an empty list when the namespace has none or doesn't exist
     */
    List<OperateWorkload> listWorkloads(String namespace);
}
