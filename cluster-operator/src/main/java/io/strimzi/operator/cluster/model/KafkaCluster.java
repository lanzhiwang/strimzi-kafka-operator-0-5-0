/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.model;

import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.AffinityBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.extensions.StatefulSet;
import io.strimzi.api.kafka.model.EphemeralStorage;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaAssembly;
import io.strimzi.api.kafka.model.PersistentClaimStorage;
import io.strimzi.api.kafka.model.Rack;
import io.strimzi.api.kafka.model.Resources;
import io.strimzi.api.kafka.model.Sidecar;
import io.strimzi.certs.CertAndKey;
import io.strimzi.certs.CertManager;
import io.strimzi.certs.Subject;
import io.strimzi.operator.cluster.operator.resource.ClusterRoleBindingOperator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class KafkaCluster extends AbstractModel {

    protected static final String INIT_NAME = "kafka-init";
    protected static final String RACK_VOLUME_NAME = "rack-volume";
    protected static final String RACK_VOLUME_MOUNT = "/opt/kafka/rack";
    private static final String ENV_VAR_KAFKA_INIT_RACK_TOPOLOGY_KEY = "RACK_TOPOLOGY_KEY";
    private static final String ENV_VAR_KAFKA_INIT_NODE_NAME = "NODE_NAME";

    protected static final int CLIENT_PORT = 9092;
    protected static final String CLIENT_PORT_NAME = "clients";

    protected static final int REPLICATION_PORT = 9091;
    protected static final String REPLICATION_PORT_NAME = "replication";

    protected static final int CLIENT_TLS_PORT = 9093;
    protected static final String CLIENT_TLS_PORT_NAME = "clientstls";

    protected static final String KAFKA_NAME = "kafka";
    protected static final String BROKER_CERTS_VOLUME = "broker-certs";
    protected static final String CLIENT_CA_CERTS_VOLUME = "client-ca-cert";
    protected static final String BROKER_CERTS_VOLUME_MOUNT = "/opt/kafka/broker-certs";
    protected static final String CLIENT_CA_CERTS_VOLUME_MOUNT = "/opt/kafka/client-ca-cert";
    protected static final String TLS_SIDECAR_NAME = "tls-sidecar";
    protected static final String TLS_SIDECAR_VOLUME_MOUNT = "/etc/tls-sidecar/certs/";

    private static final String NAME_SUFFIX = "-kafka";
    private static final String SERVICE_NAME_SUFFIX = NAME_SUFFIX + "-bootstrap";
    private static final String HEADLESS_SERVICE_NAME_SUFFIX = NAME_SUFFIX + "-brokers";

    // Suffixes for secrets with certificates
    private static final String SECRET_BROKERS_SUFFIX = NAME_SUFFIX + "-brokers";
    private static final String SECRET_CLUSTER_PUBLIC_KEY_SUFFIX = "-cert";
    private static final String SECRET_CLIENTS_CA_SUFFIX = "-clients-ca";
    private static final String SECRET_CLIENTS_PUBLIC_KEY_SUFFIX = "-clients-ca-cert";

    protected static final String METRICS_AND_LOG_CONFIG_SUFFIX = NAME_SUFFIX + "-config";

    // Kafka configuration
    private String zookeeperConnect;
    private Rack rack;
    private String initImage;
    private Sidecar tlsSidecar;

    // Configuration defaults
    private static final int DEFAULT_REPLICAS = 3;
    private static final int DEFAULT_HEALTHCHECK_DELAY = 15;
    private static final int DEFAULT_HEALTHCHECK_TIMEOUT = 5;
    private static final boolean DEFAULT_KAFKA_METRICS_ENABLED = false;

    // Kafka configuration keys (EnvVariables)
    public static final String ENV_VAR_KAFKA_ZOOKEEPER_CONNECT = "KAFKA_ZOOKEEPER_CONNECT";
    private static final String ENV_VAR_KAFKA_METRICS_ENABLED = "KAFKA_METRICS_ENABLED";
    protected static final String ENV_VAR_KAFKA_CONFIGURATION = "KAFKA_CONFIGURATION";
    protected static final String ENV_VAR_KAFKA_LOG_CONFIGURATION = "KAFKA_LOG_CONFIGURATION";

    private CertAndKey clientsCA;
    /**
     * Private key and certificate for each Kafka Pod name
     * used as server certificates for Kafka brokers
     */
    private Map<String, CertAndKey> brokerCerts;

    public static KafkaCluster fromCrd(CertManager certManager, KafkaAssembly kafkaAssembly, List<Secret> secrets) {
        KafkaCluster result = new KafkaCluster(
            kafkaAssembly.getMetadata().getNamespace(),
            kafkaAssembly.getMetadata().getName(),
            Labels.fromResource(kafkaAssembly).withKind(kafkaAssembly.getKind())
        );

        Kafka kafka = kafkaAssembly.getSpec().getKafka();

        result.setReplicas(kafka.getReplicas());

        String image = kafka.getImage();
        if (image == null) {
            image = Kafka.DEFAULT_IMAGE;
        }
        result.setImage(image);

        if (kafka.getReadinessProbe() != null) {
            result.setReadinessInitialDelay(
                kafka.getReadinessProbe().getInitialDelaySeconds()
            );
            result.setReadinessTimeout(
                kafka.getReadinessProbe().getTimeoutSeconds()
            );
        }

        if (kafka.getLivenessProbe() != null) {
            result.setLivenessInitialDelay(
                kafka.getLivenessProbe().getInitialDelaySeconds()
            );
            result.setLivenessTimeout(
                kafka.getLivenessProbe().getTimeoutSeconds()
            );
        }

        result.setRack(kafka.getRack());

        String initImage = kafka.getBrokerRackInitImage();
        if (initImage == null) {
            initImage = Kafka.DEFAULT_INIT_IMAGE;
        }
        result.setInitImage(initImage);

        result.setLogging(kafka.getLogging());

        result.setJvmOptions(kafka.getJvmOptions());

        result.setConfiguration(new KafkaConfiguration(kafka.getConfig().entrySet()));

        Map<String, Object> metrics = kafka.getMetrics();
        if (metrics != null && !metrics.isEmpty()) {
            result.setMetricsEnabled(true);
            result.setMetricsConfig(metrics.entrySet());
        }

        result.setStorage(kafka.getStorage());

        result.setUserAffinity(kafka.getAffinity());

        result.setResources(kafka.getResources());

        result.setTolerations(kafka.getTolerations());

        result.generateCertificates(certManager, secrets);

        result.setTlsSidecar(kafka.getTlsSidecar());

        return result;
    }

    // cluster = my-cluster
    private KafkaCluster(String namespace, String cluster, Labels labels) {
        super(namespace, cluster, labels);
        this.name = kafkaClusterName(cluster);  // my-cluster-kafka
        this.serviceName = serviceName(cluster);  // my-cluster-kafka-bootstrap
        this.headlessServiceName = headlessServiceName(cluster);  // my-cluster-kafka-brokers
        this.ancillaryConfigName = metricAndLogConfigsName(cluster);  // my-cluster-kafka-config
        this.image = Kafka.DEFAULT_IMAGE;  // io.strimzi.api.kafka.model.Kafka;
        this.replicas = DEFAULT_REPLICAS;  // DEFAULT_REPLICAS = 3;
        this.readinessTimeout = DEFAULT_HEALTHCHECK_TIMEOUT;  // DEFAULT_HEALTHCHECK_TIMEOUT = 5;
        this.readinessInitialDelay = DEFAULT_HEALTHCHECK_DELAY;  // DEFAULT_HEALTHCHECK_DELAY = 15;
        this.livenessTimeout = DEFAULT_HEALTHCHECK_TIMEOUT;  // DEFAULT_HEALTHCHECK_TIMEOUT = 5;
        this.livenessInitialDelay = DEFAULT_HEALTHCHECK_DELAY;  // DEFAULT_HEALTHCHECK_DELAY = 15;
        this.isMetricsEnabled = DEFAULT_KAFKA_METRICS_ENABLED;  // DEFAULT_KAFKA_METRICS_ENABLED = false;

        setZookeeperConnect(ZookeeperCluster.serviceName(cluster) + ":2181");  // my-cluster-zookeeper-client:2181

        this.mountPath = "/var/lib/kafka";

        this.logAndMetricsConfigVolumeName = "kafka-metrics-and-logging";
        this.logAndMetricsConfigMountPath = "/opt/kafka/custom-config/";

        this.initImage = Kafka.DEFAULT_INIT_IMAGE;
        this.validLoggerFields = getDefaultLogConfig();  // protected Properties getDefaultLogConfig() {
    }

    public static String kafkaClusterName(String cluster) {
        // private static final String NAME_SUFFIX = "-kafka";
        return cluster + KafkaCluster.NAME_SUFFIX;
    }

    public static String serviceName(String cluster) {
        // private static final String NAME_SUFFIX = "-kafka";
        // private static final String SERVICE_NAME_SUFFIX = NAME_SUFFIX + "-bootstrap";
        return cluster + KafkaCluster.SERVICE_NAME_SUFFIX;
    }

    public static String headlessServiceName(String cluster) {
        // private static final String NAME_SUFFIX = "-kafka";
        // private static final String HEADLESS_SERVICE_NAME_SUFFIX = NAME_SUFFIX + "-brokers";
        return cluster + KafkaCluster.HEADLESS_SERVICE_NAME_SUFFIX;
    }

    public static String metricAndLogConfigsName(String cluster) {
        // private static final String NAME_SUFFIX = "-kafka";
        // protected static final String METRICS_AND_LOG_CONFIG_SUFFIX = NAME_SUFFIX + "-config";
        return cluster + KafkaCluster.METRICS_AND_LOG_CONFIG_SUFFIX;
    }

    // zookeeperConnect = my-cluster-zookeeper-client:2181
    protected void setZookeeperConnect(String zookeeperConnect) {
        this.zookeeperConnect = zookeeperConnect;  // my-cluster-zookeeper-client:2181
    }

    @Override
    protected String getDefaultLogConfigFileName() {
        return "kafkaDefaultLoggingProperties";
    }

    protected void setRack(Rack rack) {
        this.rack = rack;
    }

    protected void setInitImage(String initImage) {
        this.initImage = initImage;
    }

    public void generateCertificates(CertManager certManager, List<Secret> secrets) {
        log.debug("Generating certificates");

        try {
            // my-cluster-cert
            Optional<Secret> clusterCAsecret = secrets.stream().filter(
                s -> s.getMetadata().getName().equals(getClusterCaName(cluster))
            ).findFirst();

            if (clusterCAsecret.isPresent()) {  // my-cluster-cert secret ??????
                // get the generated CA private key + self-signed certificate for each broker
                clusterCA = new CertAndKey(
                    decodeFromSecret(clusterCAsecret.get(), "cluster-ca.key"),
                    decodeFromSecret(clusterCAsecret.get(), "cluster-ca.crt")
                );

                // CA private key + self-signed certificate for clients communications
                // my-cluster-clients-ca
                Optional<Secret> clientsCAsecret = secrets.stream().filter(
                    s -> s.getMetadata().getName().equals(KafkaCluster.clientsCASecretName(cluster))
                ).findFirst();

                if (!clientsCAsecret.isPresent()) {  // my-cluster-clients-ca secret ?????????
                    log.debug("Clients CA to generate");
                    File clientsCAkeyFile = File.createTempFile("tls", "clients-ca-key");
                    File clientsCAcertFile = File.createTempFile("tls", "clients-ca-cert");

                    Subject sbj = new Subject();
                    sbj.setOrganizationName("io.strimzi");
                    sbj.setCommonName("kafka-clients-ca");

                    certManager.generateSelfSignedCert(
                        clientsCAkeyFile,
                        clientsCAcertFile,
                        sbj,
                        CERTS_EXPIRATION_DAYS
                    );
                    clientsCA = new CertAndKey(
                        Files.readAllBytes(clientsCAkeyFile.toPath()),
                        Files.readAllBytes(clientsCAcertFile.toPath())
                    );
                    if (!clientsCAkeyFile.delete()) {
                        log.warn("{} cannot be deleted", clientsCAkeyFile.getName());
                    }
                    if (!clientsCAcertFile.delete()) {
                        log.warn("{} cannot be deleted", clientsCAcertFile.getName());
                    }
                } else {
                    log.debug("Clients CA already exists");
                    clientsCA = new CertAndKey(
                        decodeFromSecret(clientsCAsecret.get(), "clients-ca.key"),
                        decodeFromSecret(clientsCAsecret.get(), "clients-ca.crt")
                    );
                }

                // recover or generates the private key + certificate for each broker for internal and clients communication
                // // my-cluster-kafka-brokers
                Optional<Secret> clusterSecret = secrets.stream().filter(
                    s -> s.getMetadata().getName().equals(KafkaCluster.brokersSecretName(cluster))
                ).findFirst();

                int replicasInternalSecret = !clusterSecret.isPresent() ? 0 : (clusterSecret.get().getData().size() - 1) / 2;

                log.debug("Internal communication certificates");
                // private Map<String, CertAndKey> brokerCerts;
                brokerCerts = maybeCopyOrGenerateCerts(certManager, clusterSecret, replicasInternalSecret, clusterCA, KafkaCluster::kafkaPodName);
            } else {
                throw new NoCertificateSecretException("The cluster CA certificate Secret is missing");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        log.debug("End generating certificates");
    }

    protected void setTlsSidecar(Sidecar tlsSidecar) {
        this.tlsSidecar = tlsSidecar;
    }

    public static String clusterPublicKeyName(String cluster) {
        // private static final String SECRET_CLUSTER_PUBLIC_KEY_SUFFIX = "-cert";
        return getClusterCaName(cluster) + KafkaCluster.SECRET_CLUSTER_PUBLIC_KEY_SUFFIX;  // my-cluster-cert
    }

    public static String clientsCASecretName(String cluster) {
        // private static final String SECRET_CLIENTS_CA_SUFFIX = "-clients-ca";
        return cluster + KafkaCluster.SECRET_CLIENTS_CA_SUFFIX;  // my-cluster-clients-ca
    }

    public static String brokersSecretName(String cluster) {
        // private static final String NAME_SUFFIX = "-kafka";
        // private static final String SECRET_BROKERS_SUFFIX = NAME_SUFFIX + "-brokers";
        return cluster + KafkaCluster.SECRET_BROKERS_SUFFIX;  // my-cluster-kafka-brokers
    }

    public static String kafkaPodName(String cluster, int pod) {
        return kafkaClusterName(cluster) + "-" + pod;  // my-cluster-kafka-0  my-cluster-kafka-1  my-cluster-kafka-2
    }

    /**
    getLogging()
    generateMetricsAndLogConfigMap()  // todo
    generateService()
    generateHeadlessService()
    generateStatefulSet(isOpenShift)
    generateClientsCASecret()
    generateClientsPublicKeySecret()
    generateClusterPublicKeySecret()
    generateBrokersSecret()
     */

    public Service generateService() {
        // createService("ClusterIP", List<ServicePort>, Map<String, String>)
        return createService("ClusterIP", getServicePorts(), getPrometheusAnnotations());
    }

    private List<ServicePort> getServicePorts() {
        /**
        createServicePort(clients, 9092, 9092, "TCP")
        createServicePort(clientstls, 9093, 9093, "TCP")
        createServicePort(replication, 9091, 9091, "TCP")
        createServicePort(kafkametrics, 9404, 9404, "TCP")
         */
        List<ServicePort> ports = new ArrayList<>(2);
        ports.add(createServicePort(CLIENT_PORT_NAME, CLIENT_PORT, CLIENT_PORT, "TCP"));
        ports.add(createServicePort(CLIENT_TLS_PORT_NAME, CLIENT_TLS_PORT, CLIENT_TLS_PORT, "TCP"));
        ports.add(createServicePort(REPLICATION_PORT_NAME, REPLICATION_PORT, REPLICATION_PORT, "TCP"));
        if (isMetricsEnabled()) {
            ports.add(createServicePort(metricsPortName, metricsPort, metricsPort, "TCP"));
        }
        return ports;
    }

    public Service generateHeadlessService() {
        Map<String, String> annotations = Collections.singletonMap("service.alpha.kubernetes.io/tolerate-unready-endpoints", "true");
        return createHeadlessService(getHeadlessServicePorts(), annotations);
    }

    private List<ServicePort> getHeadlessServicePorts() {
        List<ServicePort> ports = new ArrayList<>(2);
        ports.add(createServicePort(CLIENT_PORT_NAME, CLIENT_PORT, CLIENT_PORT, "TCP"));
        ports.add(createServicePort(REPLICATION_PORT_NAME, REPLICATION_PORT, REPLICATION_PORT, "TCP"));
        ports.add(createServicePort(CLIENT_TLS_PORT_NAME, CLIENT_TLS_PORT, CLIENT_TLS_PORT, "TCP"));
        return ports;
    }

    public StatefulSet generateStatefulSet(boolean isOpenShift) {
        // createStatefulSet(List<Volume>, List<PersistentVolumeClaim>, List<VolumeMount>, Affinity, List<Container>, List<Container>)
        return createStatefulSet(
            getVolumes(),
            getVolumeClaims(),
            getVolumeMounts(),
            getMergedAffinity(),
            getInitContainers(),
            getContainers(),
            isOpenShift
        );
    }

    private List<Volume> getVolumes() {
        List<Volume> volumeList = new ArrayList<>();
        if (storage instanceof EphemeralStorage) {
            volumeList.add(createEmptyDirVolume(VOLUME_NAME));
        }

        if (rack != null) {
            volumeList.add(createEmptyDirVolume(RACK_VOLUME_NAME));
        }
        volumeList.add(createSecretVolume(BROKER_CERTS_VOLUME, KafkaCluster.brokersSecretName(cluster)));
        volumeList.add(createSecretVolume(CLIENT_CA_CERTS_VOLUME, KafkaCluster.clientsPublicKeyName(cluster)));
        volumeList.add(createConfigMapVolume(logAndMetricsConfigVolumeName, ancillaryConfigName));

        return volumeList;
    }

    private List<PersistentVolumeClaim> getVolumeClaims() {
        List<PersistentVolumeClaim> pvcList = new ArrayList<>();
        if (storage instanceof PersistentClaimStorage) {
            pvcList.add(createPersistentVolumeClaim(VOLUME_NAME));
        }
        return pvcList;
    }

    private List<VolumeMount> getVolumeMounts() {
        List<VolumeMount> volumeMountList = new ArrayList<>();
        volumeMountList.add(createVolumeMount(VOLUME_NAME, mountPath));

        volumeMountList.add(createVolumeMount(BROKER_CERTS_VOLUME, BROKER_CERTS_VOLUME_MOUNT));
        volumeMountList.add(createVolumeMount(CLIENT_CA_CERTS_VOLUME, CLIENT_CA_CERTS_VOLUME_MOUNT));
        volumeMountList.add(createVolumeMount(logAndMetricsConfigVolumeName, logAndMetricsConfigMountPath));

        if (rack != null) {
            volumeMountList.add(createVolumeMount(RACK_VOLUME_NAME, RACK_VOLUME_MOUNT));
        }

        return volumeMountList;
    }

    @Override
    protected Affinity getMergedAffinity() {
        Affinity userAffinity = getUserAffinity();
        AffinityBuilder builder = new AffinityBuilder(userAffinity == null ? new Affinity() : userAffinity);
        if (rack != null) {
            // If there's a rack config, we need to add a podAntiAffinity to spread the brokers among the racks
            builder = builder
                    .editOrNewPodAntiAffinity()
                        .addNewPreferredDuringSchedulingIgnoredDuringExecution()
                            .withWeight(100)
                            .withNewPodAffinityTerm()
                                .withTopologyKey(rack.getTopologyKey())
                                .withNewLabelSelector()
                                    .addToMatchLabels(Labels.STRIMZI_CLUSTER_LABEL, cluster)
                                    .addToMatchLabels(Labels.STRIMZI_NAME_LABEL, name)
                                .endLabelSelector()
                            .endPodAffinityTerm()
                        .endPreferredDuringSchedulingIgnoredDuringExecution()
                    .endPodAntiAffinity();
        }
        return builder.build();
    }

    @Override
    protected List<Container> getInitContainers() {

        List<Container> initContainers = new ArrayList<>();

        if (rack != null) {

            ResourceRequirements resources = new ResourceRequirementsBuilder()
                    .addToRequests("cpu", new Quantity("100m"))
                    .addToRequests("memory", new Quantity("128Mi"))
                    .addToLimits("cpu", new Quantity("1"))
                    .addToLimits("memory", new Quantity("256Mi"))
                    .build();

            List<EnvVar> varList =
                    Arrays.asList(buildEnvVarFromFieldRef(ENV_VAR_KAFKA_INIT_NODE_NAME, "spec.nodeName"),
                            buildEnvVar(ENV_VAR_KAFKA_INIT_RACK_TOPOLOGY_KEY, rack.getTopologyKey()));

            Container initContainer = new ContainerBuilder()
                    .withName(INIT_NAME)
                    .withImage(initImage)
                    .withResources(resources)
                    .withEnv(varList)
                    .withVolumeMounts(createVolumeMount(RACK_VOLUME_NAME, RACK_VOLUME_MOUNT))
                    .build();

            initContainers.add(initContainer);
        }

        return initContainers;
    }

    @Override
    protected List<Container> getContainers() {

        List<Container> containers = new ArrayList<>();

        Container container = new ContainerBuilder()
                .withName(KAFKA_NAME)
                .withImage(getImage())
                .withEnv(getEnvVars())
                .withVolumeMounts(getVolumeMounts())
                .withPorts(getContainerPortList())
                .withLivenessProbe(createTcpSocketProbe(REPLICATION_PORT, livenessInitialDelay, livenessTimeout))
                .withReadinessProbe(createTcpSocketProbe(REPLICATION_PORT, readinessInitialDelay, readinessTimeout))
                .withResources(resources(getResources()))
                .build();

        String tlsSidecarImage = (tlsSidecar != null && tlsSidecar.getImage() != null) ?
                tlsSidecar.getImage() : Kafka.DEFAULT_TLS_SIDECAR_IMAGE;

        Resources tlsSidecarResources = (tlsSidecar != null) ? tlsSidecar.getResources() : null;

        Container tlsSidecarContainer = new ContainerBuilder()
                .withName(TLS_SIDECAR_NAME)
                .withImage(tlsSidecarImage)
                .withResources(resources(tlsSidecarResources))
                .withEnv(singletonList(buildEnvVar(ENV_VAR_KAFKA_ZOOKEEPER_CONNECT, zookeeperConnect)))
                .withVolumeMounts(createVolumeMount(BROKER_CERTS_VOLUME, TLS_SIDECAR_VOLUME_MOUNT))
                .build();

        containers.add(container);
        containers.add(tlsSidecarContainer);

        return containers;
    }

    public Secret generateClientsCASecret() {
        Map<String, String> data = new HashMap<>();
        data.put("clients-ca.key", Base64.getEncoder().encodeToString(clientsCA.key()));
        data.put("clients-ca.crt", Base64.getEncoder().encodeToString(clientsCA.cert()));
        return createSecret(KafkaCluster.clientsCASecretName(cluster), data);
    }

    public Secret generateClientsPublicKeySecret() {
        Map<String, String> data = new HashMap<>();
        data.put("ca.crt", Base64.getEncoder().encodeToString(clientsCA.cert()));
        return createSecret(KafkaCluster.clientsPublicKeyName(cluster), data);
    }

    public static String clientsPublicKeyName(String cluster) {
        return cluster + KafkaCluster.SECRET_CLIENTS_PUBLIC_KEY_SUFFIX;
    }

    public Secret generateClusterPublicKeySecret() {
        Map<String, String> data = new HashMap<>();
        data.put("ca.crt", Base64.getEncoder().encodeToString(clusterCA.cert()));
        return createSecret(KafkaCluster.clusterPublicKeyName(cluster), data);
    }

    public Secret generateBrokersSecret() {
        Base64.Encoder encoder = Base64.getEncoder();

        Map<String, String> data = new HashMap<>();
        data.put("cluster-ca.crt", encoder.encodeToString(clusterCA.cert()));

        for (int i = 0; i < replicas; i++) {
            CertAndKey cert = brokerCerts.get(KafkaCluster.kafkaPodName(cluster, i));
            data.put(KafkaCluster.kafkaPodName(cluster, i) + ".key", encoder.encodeToString(cert.key()));
            data.put(KafkaCluster.kafkaPodName(cluster, i) + ".crt", encoder.encodeToString(cert.cert()));
        }
        return createSecret(KafkaCluster.brokersSecretName(cluster), data);
    }

    private List<ContainerPort> getContainerPortList() {
        List<ContainerPort> portList = new ArrayList<>(3);
        portList.add(createContainerPort(CLIENT_PORT_NAME, CLIENT_PORT, "TCP"));
        portList.add(createContainerPort(REPLICATION_PORT_NAME, REPLICATION_PORT, "TCP"));
        portList.add(createContainerPort(CLIENT_TLS_PORT_NAME, CLIENT_TLS_PORT, "TCP"));
        if (isMetricsEnabled) {
            portList.add(createContainerPort(metricsPortName, metricsPort, "TCP"));
        }

        return portList;
    }

    @Override
    protected List<EnvVar> getEnvVars() {
        List<EnvVar> varList = new ArrayList<>();
        varList.add(buildEnvVar(ENV_VAR_KAFKA_ZOOKEEPER_CONNECT, zookeeperConnect));
        varList.add(buildEnvVar(ENV_VAR_KAFKA_METRICS_ENABLED, String.valueOf(isMetricsEnabled)));
        heapOptions(varList, 0.5, 5L * 1024L * 1024L * 1024L);
        jvmPerformanceOptions(varList);

        if (configuration != null && !configuration.getConfiguration().isEmpty()) {
            varList.add(buildEnvVar(ENV_VAR_KAFKA_CONFIGURATION, configuration.getConfiguration()));
        }
        // A hack to force rolling when the logging config changes
        if (getLogging() != null && getLogging().getCm() != null) {
            varList.add(buildEnvVar(ENV_VAR_KAFKA_LOG_CONFIGURATION, getLogging().getCm().toString()));
        }

        return varList;
    }

    @Override
    protected String getServiceAccountName() {
        return initContainerServiceAccountName(cluster);
    }

    public ServiceAccount generateInitContainerServiceAccount() {
        return new ServiceAccountBuilder()
                .withNewMetadata()
                    .withName(initContainerServiceAccountName(cluster))
                    .addToLabels("app", "strimzi")
                    .withNamespace(namespace)
                .endMetadata()
            .build();
    }

    public static String initContainerServiceAccountName(String kafkaResourceName) {
        return kafkaClusterName(kafkaResourceName);
    }

    public static String initContainerClusterRoleBindingName(String kafkaResourceName) {
        return "strimzi-" + kafkaResourceName + "-kafka-init";
    }

    public ClusterRoleBindingOperator.ClusterRoleBinding generateClusterRoleBinding(String assemblyNamespace) {
        if (rack != null) {
            return new ClusterRoleBindingOperator.ClusterRoleBinding(
                    initContainerClusterRoleBindingName(cluster),
                    "strimzi-kafka-broker",
                    assemblyNamespace, initContainerServiceAccountName(cluster));
        } else {
            return null;
        }
    }

}
