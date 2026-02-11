package com.lab.infra.stacks;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.CfnOutputProps;


import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;

import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.ClusterProps;
import software.amazon.awscdk.services.eks.EndpointAccess;
import software.amazon.awscdk.services.eks.FargateProfileProps;
import software.amazon.awscdk.services.eks.HelmChart;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.eks.Selector;
import software.amazon.awscdk.services.eks.ServiceAccount;
import software.amazon.awscdk.services.eks.ServiceAccountOptions;

import software.amazon.awscdk.services.iam.PolicyStatement;

import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.LayerVersion;
import software.amazon.awscdk.services.lambda.Runtime;

import software.constructs.Construct;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EksStack extends Stack {

    private final Cluster cluster;
    public final HelmChart albControllerChart;


    public EksStack(final Construct scope, final String id, final StackProps props, final EksStackProps eksProps) {
        super(scope, id, props);

        Vpc vpc = eksProps.getVpc();
        SecurityGroup eksSecurityGroup = eksProps.getEksSecurityGroup();

        // Kubectl Lambda Layer (required for CDK Kubernetes manifests)
        LayerVersion kubectlLayer = LayerVersion.Builder.create(this, "KubectlLayer")
                .code(Code.fromAsset("kubectl-layer"))
                .compatibleRuntimes(List.of(Runtime.PYTHON_3_13, Runtime.PROVIDED_AL2))
                .description("Kubectl 1.28 layer")
                .build();

        // EKS Cluster (Fargate only)
        this.cluster = new Cluster(this, "KongMicroservicesCluster", ClusterProps.builder()
                .version(KubernetesVersion.V1_28)
                .vpc(vpc)
                .vpcSubnets(List.of(
                        SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build()
                ))
                .defaultCapacity(0)
                .clusterName("kong-microservices-cluster")
                .endpointAccess(EndpointAccess.PUBLIC_AND_PRIVATE)
                .securityGroup(eksSecurityGroup)
                .kubectlLayer(kubectlLayer)
                .build());

        // Fargate Profiles
        addFargateProfile("KongFargateProfile", "kong");
        addFargateProfile("MicroservicesFargateProfile", "microservices");
        addFargateProfile("DatabasesFargateProfile", "databases");
        addFargateProfile("CoreSystemFargateProfile", "kube-system", "kube-public");

        // ================= AWS LOAD BALANCER CONTROLLER =================

        PolicyStatement albPolicy = PolicyStatement.Builder.create()
                .actions(List.of(
                        "iam:CreateServiceLinkedRole",
                        "ec2:Describe*",
                        "ec2:CreateSecurityGroup",
                        "ec2:CreateTags",
                        "ec2:AuthorizeSecurityGroupIngress",
                        "ec2:RevokeSecurityGroupIngress",
                        "ec2:DeleteSecurityGroup",
                        "elasticloadbalancing:*",
                        "waf-regional:*",
                        "wafv2:*",
                        "shield:*"
                ))
                .resources(List.of("*"))
                .build();

        ServiceAccount albSa = cluster.addServiceAccount("AwsLoadBalancerControllerSA",
                ServiceAccountOptions.builder()
                        .name("aws-load-balancer-controller")
                        .namespace("kube-system")
                        .build());

        //albSa.getRole().addToPolicy(albPolicy);
        albSa.getRole().addToPrincipalPolicy(albPolicy);

        // HelmChart albController = HelmChart.Builder.create(this, "AwsLoadBalancerController")
        //         .cluster(cluster)
        //         .chart("aws-load-balancer-controller")
        //         .repository("https://aws.github.io/eks-charts")
        //         .namespace("kube-system")
        //         .release("aws-load-balancer-controller")
        //         .createNamespace(false)
        //         .values(Map.of(
        //                 "clusterName", cluster.getClusterName(),
        //                 "serviceAccount", Map.of(
        //                         "create", false,
        //                         "name", "aws-load-balancer-controller"
        //                 ),
        //                 "region", Stack.of(this).getRegion(),
        //                 "vpcId", vpc.getVpcId()
        //         ))
        //         .build();

        // albController.getNode().addDependency(albSa);

        this.albControllerChart = HelmChart.Builder.create(this, "AwsLoadBalancerController")
                .cluster(cluster)
                .chart("aws-load-balancer-controller")
                .repository("https://aws.github.io/eks-charts")
                .namespace("kube-system")
                .release("aws-load-balancer-controller")
                .createNamespace(false)
                .values(Map.of(
                        "clusterName", cluster.getClusterName(),
                        "serviceAccount", Map.of(
                                "create", false,
                                "name", "aws-load-balancer-controller"
                        ),
                        "region", Stack.of(this).getRegion(),
                        "vpcId", vpc.getVpcId()
                ))
                .build();

        albControllerChart.getNode().addDependency(albSa);

        

        // Outputs
        new CfnOutput(this, "ClusterName", CfnOutputProps.builder()
                .value(cluster.getClusterName())
                .description("EKS Cluster Name")
                .build());

        new CfnOutput(this, "ClusterEndpoint", CfnOutputProps.builder()
                .value(cluster.getClusterEndpoint())
                .description("EKS Cluster Endpoint")
                .build());
    }

    public HelmChart getAlbControllerChart() {
        return albControllerChart;
    }

   private void addFargateProfile(String id, String... namespaces) {
    cluster.addFargateProfile(id, FargateProfileProps.builder()
            .cluster(cluster)   // ⭐ REQUIRED IN JAVA CDK
            .selectors(Arrays.stream(namespaces)
                    .map(ns -> Selector.builder().namespace(ns).build())
                    .collect(Collectors.toList()))
            .subnetSelection(SubnetSelection.builder()
                    .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                    .build())
            .build());
    }


    public Cluster getCluster() {
        return cluster;
    }
}
