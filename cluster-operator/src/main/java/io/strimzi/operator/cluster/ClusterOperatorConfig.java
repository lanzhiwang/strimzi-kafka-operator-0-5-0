/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;

/**
 * Cluster Operator configuration
 */
public class ClusterOperatorConfig {

    public static final String STRIMZI_NAMESPACE = "STRIMZI_NAMESPACE";
    public static final String STRIMZI_FULL_RECONCILIATION_INTERVAL_MS = "STRIMZI_FULL_RECONCILIATION_INTERVAL_MS";
    public static final String STRIMZI_OPERATION_TIMEOUT_MS = "STRIMZI_OPERATION_TIMEOUT_MS";

    public static final long DEFAULT_FULL_RECONCILIATION_INTERVAL_MS = 120_000;
    public static final long DEFAULT_OPERATION_TIMEOUT_MS = 300_000;

    private final Set<String> namespaces;
    private final long reconciliationIntervalMs;
    private final long operationTimeoutMs;

    /**
     * Constructor
     *
     * @param namespaces namespace in which the operator will run and create resources
     * @param reconciliationIntervalMs    specify every how many milliseconds the reconciliation runs
     * @param operationTimeoutMs    timeout for internal operations specified in milliseconds
     */
    public ClusterOperatorConfig(Set<String> namespaces, long reconciliationIntervalMs, long operationTimeoutMs) {
        this.namespaces = unmodifiableSet(new HashSet<>(namespaces));
        this.reconciliationIntervalMs = reconciliationIntervalMs;
        this.operationTimeoutMs = operationTimeoutMs;
    }

    /**
     * Loads configuration parameters from a related map
     *
     * @param map   map from which loading configuration parameters
     * @return  Cluster Operator configuration instance
     */
    public static ClusterOperatorConfig fromMap(Map<String, String> map) {
        /**
        {
            STRIMZI_DEFAULT_ZOOKEEPER_IMAGE=strimzi/zookeeper:0.5.0,
            PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin,
            STRIMZI_DEFAULT_TLS_SIDECAR_TOPIC_OPERATOR_IMAGE=strimzi/topic-operator-stunnel:0.5.0,
            KUBERNETES_PORT=tcp://10.96.0.1:443,
            STRIMZI_DEFAULT_TOPIC_OPERATOR_IMAGE=strimzi/topic-operator:0.5.0,
            JAVA_HOME=/usr/lib/jvm/java,
            STRIMZI_FULL_RECONCILIATION_INTERVAL_MS=120000,
            KUBERNETES_SERVICE_HOST=10.96.0.1,
            STRIMZI_DEFAULT_KAFKA_IMAGE=strimzi/kafka:0.5.0,
            STRIMZI_DEFAULT_KAFKA_CONNECT_IMAGE=strimzi/kafka-connect:0.5.0,
            STRIMZI_DEFAULT_TLS_SIDECAR_KAFKA_IMAGE=strimzi/kafka-stunnel:0.5.0,
            PWD=/,
            KUBERNETES_PORT_443_TCP=tcp://10.96.0.1:443,
            STRIMZI_OPERATION_TIMEOUT_MS=300000,
            KUBERNETES_PORT_443_TCP_ADDR=10.96.0.1,
            STRIMZI_DEFAULT_KAFKA_CONNECT_S2I_IMAGE=strimzi/kafka-connect-s2i:0.5.0,
            STRIMZI_VERSION=0.5.0,
            STRIMZI_NAMESPACE=default,
            KUBERNETES_PORT_443_TCP_PROTO=tcp,
            KUBERNETES_SERVICE_PORT=443,
            STRIMZI_DEFAULT_KAFKA_INIT_IMAGE=strimzi/kafka-init:0.5.0,
            STRIMZI_LOG_LEVEL=INFO,
            HOSTNAME=strimzi-cluster-operator-7bdb776c8d-rnprs,
            KUBERNETES_PORT_443_TCP_PORT=443,
            KUBERNETES_SERVICE_PORT_HTTPS=443,
            SHLVL=1,
            HOME=/root,
            STRIMZI_DEFAULT_TLS_SIDECAR_ZOOKEEPER_IMAGE=strimzi/zookeeper-stunnel:0.5.0,
            MALLOC_ARENA_MAX=2
        }
         */
        String namespacesList = map.get(ClusterOperatorConfig.STRIMZI_NAMESPACE);
        Set<String> namespaces;
        if (namespacesList == null || namespacesList.isEmpty()) {
            throw new IllegalArgumentException(ClusterOperatorConfig.STRIMZI_NAMESPACE + " cannot be null");
        } else {
            namespaces = new HashSet(asList(namespacesList.trim().split("\\s*,+\\s*")));
        }

        long reconciliationInterval = DEFAULT_FULL_RECONCILIATION_INTERVAL_MS;
        String reconciliationIntervalEnvVar = map.get(ClusterOperatorConfig.STRIMZI_FULL_RECONCILIATION_INTERVAL_MS);
        if (reconciliationIntervalEnvVar != null) {
            reconciliationInterval = Long.parseLong(reconciliationIntervalEnvVar);
        }

        long operationTimeout = DEFAULT_OPERATION_TIMEOUT_MS;
        String operationTimeoutEnvVar = map.get(ClusterOperatorConfig.STRIMZI_OPERATION_TIMEOUT_MS);
        if (operationTimeoutEnvVar != null) {
            operationTimeout = Long.parseLong(operationTimeoutEnvVar);
        }

        return new ClusterOperatorConfig(namespaces, reconciliationInterval, operationTimeout);
    }


    /**
     * @return  namespaces in which the operator runs and creates resources
     */
    public Set<String> getNamespaces() {
        return namespaces;
    }

    /**
     * @return  how many milliseconds the reconciliation runs
     */
    public long getReconciliationIntervalMs() {
        return reconciliationIntervalMs;
    }

    /**
     * @return  how many milliseconds should we wait for Kubernetes operations
     */
    public long getOperationTimeoutMs() {
        return operationTimeoutMs;
    }

    @Override
    public String toString() {
        return "ClusterOperatorConfig(" +
                "namespaces=" + namespaces +
                ",reconciliationIntervalMs=" + reconciliationIntervalMs +
                ")";
    }
}
