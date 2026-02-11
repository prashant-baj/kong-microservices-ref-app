package com.lab.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.eks.*;
import software.constructs.Construct;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;


public class DatabaseStack extends Stack {

    public DatabaseStack(final Construct scope, final String id, final StackProps props, final DatabaseStackProps dbProps) {
        super(scope, id, props);

        Cluster cluster = dbProps.getCluster();

        // -----------------------------
        // 1️⃣ Create databases namespace
        // -----------------------------
        Map<String, Object> namespaceManifest = new LinkedHashMap<>();
        namespaceManifest.put("apiVersion", "v1");
        namespaceManifest.put("kind", "Namespace");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", "databases");
        namespaceManifest.put("metadata", metadata);

        KubernetesManifest dbNamespace =
                cluster.addManifest("DatabaseNamespace", namespaceManifest);

        // -----------------------------
        // 2️⃣ PostgreSQL StatefulSet
        // -----------------------------
        KubernetesManifest postgresSts = cluster.addManifest(
                "PostgresStatefulSet",
                createPostgresStatefulSetManifest()
        );
        postgresSts.getNode().addDependency(dbNamespace);

        // -----------------------------
        // 3️⃣ PostgreSQL Headless Service
        // -----------------------------
        KubernetesManifest postgresSvc = cluster.addManifest(
                "PostgresService",
                createPostgresServiceManifest()
        );
        postgresSvc.getNode().addDependency(dbNamespace);

        // -----------------------------
        // 4️⃣ Init Script ConfigMap
        // -----------------------------
        KubernetesManifest postgresInit = cluster.addManifest(
                "PostgresInitScript",
                createPostgresInitConfigMap()
        );
        postgresInit.getNode().addDependency(dbNamespace);

        // Outputs
        new CfnOutput(this, "PostgresDatabaseEndpoint",
                CfnOutputProps.builder()
                        .value("postgres.databases.svc.cluster.local:5432")
                        .description("PostgreSQL Database Endpoint (internal DNS)")
                        .build());
    }

    // ================= POSTGRES STATEFULSET =================

    private Map<String, Object> createPostgresStatefulSetManifest() {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "apps/v1");
        manifest.put("kind", "StatefulSet");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("name", "postgres");
        metadata.put("namespace", "databases");
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("serviceName", "postgres");
        spec.put("replicas", 1);

        Map<String, Object> selector = Map.of("matchLabels", Map.of("app", "postgres"));
        spec.put("selector", selector);

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("metadata", Map.of("labels", Map.of("app", "postgres")));

        Map<String, Object> container = createPostgresContainer();
        template.put("spec", Map.of(
                "containers", List.of(container),
                "volumes", List.of(Map.of(
                        "name", "init-scripts",
                        "configMap", Map.of("name", "postgres-init-script")
                ))
        ));

        spec.put("template", template);

        spec.put("volumeClaimTemplates", List.of(Map.of(
                "metadata", Map.of("name", "postgres-storage"),
                "spec", Map.of(
                        "accessModes", List.of("ReadWriteOnce"),
                        "resources", Map.of("requests", Map.of("storage", "10Gi"))
                )
        )));

        manifest.put("spec", spec);
        return manifest;
    }

    private Map<String, Object> createPostgresContainer() {
        return Map.of(
                "name", "postgres",
                "image", "postgres:16-alpine",
                "ports", List.of(Map.of("containerPort", 5432, "name", "postgres")),
                "env", List.of(
                        Map.of("name", "POSTGRES_USER", "value", "postgres"),
                        Map.of("name", "POSTGRES_PASSWORD", "value", "postgres"),
                        Map.of("name", "PGDATA", "value", "/var/lib/postgresql/data/pgdata")
                ),
                "volumeMounts", List.of(
                        Map.of("name", "postgres-storage", "mountPath", "/var/lib/postgresql/data"),
                        Map.of("name", "init-scripts", "mountPath", "/docker-entrypoint-initdb.d")
                )
        );
    }

    // ================= SERVICE =================

    private Map<String, Object> createPostgresServiceManifest() {
        return Map.of(
                "apiVersion", "v1",
                "kind", "Service",
                "metadata", Map.of("name", "postgres", "namespace", "databases"),
                "spec", Map.of(
                        "clusterIP", "None",
                        "ports", List.of(Map.of("port", 5432, "targetPort", 5432)),
                        "selector", Map.of("app", "postgres")
                )
        );
    }

    // ================= INIT SCRIPT CONFIGMAP =================

    private Map<String, Object> createPostgresInitConfigMap() {
        return Map.of(
                "apiVersion", "v1",
                "kind", "ConfigMap",
                "metadata", Map.of("name", "postgres-init-script", "namespace", "databases"),
                "data", Map.of("init-databases.sql", getInitDatabaseScript())
        );
    }

    private String getInitDatabaseScript() {
        return "CREATE DATABASE product_db;\n" +
               "CREATE DATABASE order_db;\n" +
               "CREATE DATABASE inventory_db;\n" +
               "CREATE DATABASE kong;\n";
    }
}
