package com.lab.infra;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.eks.*;
import software.amazon.awscdk.services.iam.*;
import com.lab.infra.stacks.*;

public class InfraApp {
    public static void main(final String[] args) {
        App app = new App();

        String region = System.getenv("AWS_REGION") != null ? System.getenv("AWS_REGION") : "ap-southeast-1";
        String account = System.getenv("AWS_ACCOUNT_ID");

        StackProps stackProps = StackProps.builder()
                .env(Environment.builder()
                        .region(region)
                        .account(account)
                        .build())
                .description("Kong API Gateway and Microservices Infrastructure")
                .build();

        // Create networking stack
        NetworkingStack networkingStack = new NetworkingStack(app, "NetworkingStack", stackProps);

        // Create ECR repositories
        EcrStack ecrStack = new EcrStack(app, "EcrStack", stackProps);

        // Create EKS cluster with Fargate
        EksStackProps eksStackProps = EksStackProps.builder()
                .vpc(networkingStack.getVpc())
                .eksSecurityGroup(networkingStack.getEksSecurityGroup())
                .build();

        EksStack eksStack = new EksStack(app, "EksStack", stackProps, eksStackProps);

        // Create database stack (PostgreSQL in Kubernetes)
        DatabaseStackProps dbStackProps = DatabaseStackProps.builder()
                .cluster(eksStack.getCluster())
                .build();

        DatabaseStack databaseStack = new DatabaseStack(app, "DatabaseStack", stackProps, dbStackProps);

        // Deploy Kong API Gateway
        KongStackProps kongStackProps = KongStackProps.builder()
                .cluster(eksStack.getCluster())
                .namespace("kong")
                .build();

        KongStack kongStack = new KongStack(app, "KongStack", stackProps, kongStackProps);

        // Deploy microservices
        MicroservicesStackProps msStackProps = MicroservicesStackProps.builder()
                .cluster(eksStack.getCluster())
                .namespace("microservices")
                .albControllerChart(eksStack.getAlbControllerChart())  
                .productServiceDbHost("postgres.databases.svc.cluster.local")
                .productServiceDbPort(5432)
                .productServiceDbName("product_db")
                .productServiceImageUri(ecrStack.getProductServiceRepository().getRepositoryUri() + ":latest")
                .orderServiceDbHost("postgres.databases.svc.cluster.local")
                .orderServiceDbPort(5432)
                .orderServiceDbName("order_db")
                .orderServiceImageUri(ecrStack.getOrderServiceRepository().getRepositoryUri() + ":latest")
                .inventoryServiceDbHost("postgres.databases.svc.cluster.local")
                .inventoryServiceDbPort(5432)
                .inventoryServiceDbName("inventory_db")
                .inventoryServiceImageUri(ecrStack.getInventoryServiceRepository().getRepositoryUri() + ":latest")
                .build();

        MicroservicesStack microservicesStack = new MicroservicesStack(app, "MicroservicesStack", stackProps, msStackProps);

        // Add dependencies
        eksStack.addDependency(networkingStack);
        databaseStack.addDependency(eksStack);
        kongStack.addDependency(eksStack);
        microservicesStack.addDependency(eksStack);
        microservicesStack.addDependency(databaseStack);

        app.synth();
    }
}
