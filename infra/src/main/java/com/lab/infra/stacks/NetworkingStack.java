package com.lab.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;
import software.amazon.awscdk.services.ec2.IpAddresses;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SecurityGroupProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;


public class NetworkingStack extends Stack {
    private final Vpc vpc;
    private final SecurityGroup eksSecurityGroup;
    private final SecurityGroup dbSecurityGroup;

    public NetworkingStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create VPC
        this.vpc = new Vpc(this, "KongMicroservicesVpc", VpcProps.builder()
                .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
                .maxAzs(3)
                .natGateways(1)
                .subnetConfiguration(java.util.Arrays.asList(
                        SubnetConfiguration.builder()
                                .name("Public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("Private")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("Isolated")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .cidrMask(24)
                                .build()
                ))
                .build());

        // Security group for EKS
        this.eksSecurityGroup = new SecurityGroup(this, "EksSecurityGroup", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Security group for EKS cluster")
                .allowAllOutbound(true)
                .build());

        eksSecurityGroup.addIngressRule(
                Peer.anyIpv4(),
                Port.tcp(443),
                "Allow HTTPS from anywhere"
        );

        eksSecurityGroup.addIngressRule(
                Peer.anyIpv4(),
                Port.tcp(80),
                "Allow HTTP from anywhere"
        );

        // Security group for databases
        this.dbSecurityGroup = new SecurityGroup(this, "DatabaseSecurityGroup", SecurityGroupProps.builder()
                .vpc(vpc)
                .description("Security group for databases")
                .allowAllOutbound(true)
                .build());

        // Allow PostgreSQL from EKS
        dbSecurityGroup.addIngressRule(
                Peer.securityGroupId(eksSecurityGroup.getSecurityGroupId()),
                Port.tcp(5432),
                "Allow PostgreSQL from EKS"
        );

        // Outputs
        new CfnOutput(this, "VpcId", CfnOutputProps.builder()
                .value(vpc.getVpcId())
                .description("VPC ID")
                .build());

        new CfnOutput(this, "EksSecurityGroupId", CfnOutputProps.builder()
                .value(eksSecurityGroup.getSecurityGroupId())
                .description("EKS Security Group ID")
                .build());
    }

    public Vpc getVpc() {
        return vpc;
    }

    public SecurityGroup getEksSecurityGroup() {
        return eksSecurityGroup;
    }

    public SecurityGroup getDbSecurityGroup() {
        return dbSecurityGroup;
    }
}
