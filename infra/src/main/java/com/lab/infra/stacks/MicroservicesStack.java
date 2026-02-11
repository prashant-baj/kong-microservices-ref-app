package com.lab.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.eks.*;
import software.constructs.Construct;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;


public class MicroservicesStack extends Stack {

    private final KubernetesManifest namespaceManifest;
    private final HelmChart albChart;
    public MicroservicesStack(final Construct scope, final String id, final StackProps props, final MicroservicesStackProps msProps) {
        super(scope, id, props);
        this.albChart = msProps.getAlbControllerChart();
        Cluster cluster = msProps.getCluster();
        String namespace = msProps.getNamespace();

        // Create namespace FIRST
        this.namespaceManifest = createNamespace(cluster, namespace);

        // Deploy services
        deployService(cluster, namespace, "product-service", 8081,
                msProps.getProductServiceDbHost(),
                msProps.getProductServiceDbPort(),
                msProps.getProductServiceDbName(),
                msProps.getProductServiceImageUri());

        deployService(cluster, namespace, "inventory-service", 8082,
                msProps.getInventoryServiceDbHost(),
                msProps.getInventoryServiceDbPort(),
                msProps.getInventoryServiceDbName(),
                msProps.getInventoryServiceImageUri());

        deployOrderService(cluster, namespace,
                msProps.getOrderServiceDbHost(),
                msProps.getOrderServiceDbPort(),
                msProps.getOrderServiceDbName(),
                msProps.getOrderServiceImageUri());
    }

    private KubernetesManifest createNamespace(Cluster cluster, String namespace) {
        Map<String, Object> ns = Map.of(
                "apiVersion", "v1",
                "kind", "Namespace",
                "metadata", Map.of("name", namespace)
        );
        return cluster.addManifest("MicroservicesNamespace", ns);
    }

    private void deployService(Cluster cluster, String namespace, String name, int port,
                               String dbHost, int dbPort, String dbName, String image) {

        KubernetesManifest deployment = cluster.addManifest(name + "-Deployment",
                createDeploymentManifest(namespace, name, port, dbHost, dbPort, dbName, image));
        deployment.getNode().addDependency(namespaceManifest);

        KubernetesManifest service = cluster.addManifest(name + "-Service",
                createServiceManifest(namespace, name, port));
        service.getNode().addDependency(namespaceManifest);

        KubernetesManifest ingress = cluster.addManifest(name + "-Ingress",
                createIngressManifest(namespace, name, port));
        ingress.getNode().addDependency(namespaceManifest);
        ingress.getNode().addDependency(albChart);
    }

    private void deployOrderService(Cluster cluster, String namespace,
                                    String dbHost, int dbPort, String dbName, String image) {

        KubernetesManifest deployment = cluster.addManifest("order-service-Deployment",
                createOrderDeployment(namespace, dbHost, dbPort, dbName, image));
        deployment.getNode().addDependency(namespaceManifest);

        KubernetesManifest service = cluster.addManifest("order-service-Service",
                createServiceManifest(namespace, "order-service", 8083));
        service.getNode().addDependency(namespaceManifest);

        KubernetesManifest ingress = cluster.addManifest("order-service-Ingress",
                createIngressManifest(namespace, "order-service", 8083));
        ingress.getNode().addDependency(namespaceManifest);
        ingress.getNode().addDependency(albChart); 
    }

    private Map<String, Object> createDeploymentManifest(String namespace, String name, int port,
                                                         String dbHost, int dbPort, String dbName, String image) {

        return Map.of(
                "apiVersion", "apps/v1",
                "kind", "Deployment",
                "metadata", Map.of("name", name, "namespace", namespace),
                "spec", Map.of(
                        "replicas", 2,
                        "selector", Map.of("matchLabels", Map.of("app", name)),
                        "template", Map.of(
                                "metadata", Map.of("labels", Map.of("app", name)),
                                "spec", Map.of("containers",
                                        List.of(createContainerSpec(name, port, dbHost, dbPort, dbName, image)))
                        )
                )
        );
    }

    private Map<String, Object> createOrderDeployment(String namespace, String dbHost, int dbPort, String dbName, String image) {
        Map<String, Object> container = createContainerSpec("order-service", 8083, dbHost, dbPort, dbName, image);
        List<Map<String, Object>> env = (List<Map<String, Object>>) container.get("env");

        env.add(createEnvVar("SERVICES_PRODUCT_SERVICE_URL", "http://product-service:8081"));
        env.add(createEnvVar("SERVICES_INVENTORY_SERVICE_URL", "http://inventory-service:8082"));

        return Map.of(
                "apiVersion", "apps/v1",
                "kind", "Deployment",
                "metadata", Map.of("name", "order-service", "namespace", namespace),
                "spec", Map.of(
                        "replicas", 2,
                        "selector", Map.of("matchLabels", Map.of("app", "order-service")),
                        "template", Map.of(
                                "metadata", Map.of("labels", Map.of("app", "order-service")),
                                "spec", Map.of("containers", List.of(container))
                        )
                )
        );
    }

    private Map<String, Object> createServiceManifest(String namespace, String name, int port) {
        return Map.of(
                "apiVersion", "v1",
                "kind", "Service",
                "metadata", Map.of("name", name, "namespace", namespace),
                "spec", Map.of(
                        "type", "ClusterIP",
                        "selector", Map.of("app", name),
                        "ports", List.of(Map.of(
                                "port", port,
                                "targetPort", port,
                                "protocol", "TCP"
                        ))
                )
        );
    }

    private Map<String, Object> createIngressManifest(String namespace, String name, int port) {
        return Map.of(
                "apiVersion", "networking.k8s.io/v1",
                "kind", "Ingress",
                "metadata", Map.of(
                        "name", name,
                        "namespace", namespace,
                        "annotations", Map.of("kubernetes.io/ingress.class", "kong")
                ),
                "spec", Map.of(
                        "rules", List.of(Map.of(
                                "http", Map.of(
                                        "paths", List.of(Map.of(
                                                "path", "/api/" + name.replace("-service", ""),
                                                "pathType", "Prefix",
                                                "backend", Map.of(
                                                        "service", Map.of(
                                                                "name", name,
                                                                "port", Map.of("number", port)
                                                        )
                                                )
                                        ))
                                )
                        ))
                )
        );
    }

    private Map<String, Object> createContainerSpec(String name, int port,
                                                    String dbHost, int dbPort, String dbName, String image) {

        return Map.of(
                "name", name,
                "image", image,
                "imagePullPolicy", "Always",
                "ports", List.of(Map.of("containerPort", port)),
                "env", new ArrayList<>(List.of(
                        createEnvVar("SPRING_DATASOURCE_URL", "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName),
                        createEnvVar("SPRING_DATASOURCE_USERNAME", "postgres"),
                        createEnvVar("SPRING_DATASOURCE_PASSWORD", "postgres")
                ))
        );
    }

    private Map<String, Object> createEnvVar(String name, String value) {
        return Map.of("name", name, "value", value);
    }
}
