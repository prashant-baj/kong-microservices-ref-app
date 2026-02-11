package com.lab.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.eks.*;
import software.constructs.Construct;
import java.util.Map;
import java.util.LinkedHashMap;
import software.amazon.awscdk.services.eks.HelmChart;



public class KongStack extends Stack {

    public KongStack(final Construct scope, final String id, final StackProps props, final KongStackProps kongProps) {
        super(scope, id, props);

        Cluster cluster = kongProps.getCluster();
        String namespace = kongProps.getNamespace();

        // Deploy Kong using Helm
        

        HelmChart.Builder.create(this, "KongHelmChart")
                .cluster(cluster)
                .chart("kong")
                .repository("https://charts.konghq.com")
                .release("kong")                 // required in Java CDK
                .namespace(namespace)
                .createNamespace(true)
                .values(createKongValues())
                .build();


        // Outputs
        new CfnOutput(this, "KongNamespace", CfnOutputProps.builder()
                .value(namespace)
                .description("Kong Namespace")
                .build());

        new CfnOutput(this, "KongProxyService", CfnOutputProps.builder()
                .value("kong-kong-proxy (LoadBalancer)")
                .description("Kong Proxy Service")
                .build());

        new CfnOutput(this, "KongAdminService", CfnOutputProps.builder()
                .value("kong-kong-admin (ClusterIP)")
                .description("Kong Admin API Service")
                .build());
    }

    // private Map<String, Object> createKongValues() {
    //     Map<String, Object> values = new LinkedHashMap<>();

    //     // Kong configuration
    //     Map<String, Object> kong = new LinkedHashMap<>();
    //     kong.put("env", createKongEnvironment());
    //     values.put("kong", kong);

    //     // Proxy service
    //     Map<String, Object> proxy = new LinkedHashMap<>();
    //     proxy.put("type", "LoadBalancer");
    //     Map<String, Object> ports = new LinkedHashMap<>();
    //     ports.put("containerPort", 8000);
    //     ports.put("protocol", "TCP");
    //     proxy.put("ports", ports);
    //     values.put("proxy", proxy);

    //     // Admin service
    //     Map<String, Object> admin = new LinkedHashMap<>();
    //     admin.put("type", "ClusterIP");
    //     Map<String, Object> adminPorts = new LinkedHashMap<>();
    //     adminPorts.put("containerPort", 8001);
    //     adminPorts.put("protocol", "TCP");
    //     admin.put("ports", adminPorts);
    //     values.put("admin", admin);

    //     // Replicas
    //     values.put("replicaCount", 2);

    //     // Resources
    //     Map<String, Object> resources = new LinkedHashMap<>();
    //     Map<String, Object> requests = new LinkedHashMap<>();
    //     requests.put("cpu", "250m");
    //     requests.put("memory", "256Mi");
    //     resources.put("requests", requests);
    //     Map<String, Object> limits = new LinkedHashMap<>();
    //     limits.put("cpu", "500m");
    //     limits.put("memory", "512Mi");
    //     resources.put("limits", limits);
    //     values.put("resources", resources);

    //     return values;
    // }

    private Map<String, Object> createKongValues() {
        Map<String, Object> values = new LinkedHashMap<>();

        // --- Kong ENV (DB-less mode) ---
        Map<String, Object> kong = new LinkedHashMap<>();
        kong.put("env", Map.of(
                "database", "off",
                "proxy_listen", "0.0.0.0:8000",
                "admin_listen", "0.0.0.0:8001",
                "log_level", "info"
        ));
        values.put("kong", kong);

        // --- PROXY (PUBLIC LOAD BALANCER) ---
        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("type", "LoadBalancer");

        proxy.put("http", Map.of(
                "enabled", true,
                "servicePort", 80,
                "containerPort", 8000
        ));

        proxy.put("tls", Map.of(
                "enabled", false
        ));

        values.put("proxy", proxy);

        // --- ADMIN API (INTERNAL ONLY) ---
        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("enabled", true);
        admin.put("type", "ClusterIP");
        admin.put("http", Map.of(
                "enabled", true,
                "servicePort", 8001,
                "containerPort", 8001
        ));
        values.put("admin", admin);

        // --- INGRESS CONTROLLER ---
        Map<String, Object> ingressController = new LinkedHashMap<>();
        ingressController.put("enabled", true);
        values.put("ingressController", ingressController);

        // --- REPLICAS ---
        values.put("replicaCount", 2);

        // --- RESOURCES ---
        Map<String, Object> resources = new LinkedHashMap<>();
        resources.put("requests", Map.of("cpu", "250m", "memory", "256Mi"));
        resources.put("limits", Map.of("cpu", "500m", "memory", "512Mi"));
        values.put("resources", resources);

        return values;
    }


    private Map<String, Object> createKongEnvironment() {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("database", "off"); // DBless mode
        env.put("proxy_listen", "0.0.0.0:8000");
        env.put("admin_listen", "0.0.0.0:8001");
        env.put("log_level", "info");
        return env;
    }
}
