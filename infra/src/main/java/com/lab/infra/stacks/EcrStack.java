package com.lab.infra.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ecr.*;
import software.constructs.Construct;

public class EcrStack extends Stack {
    private final Repository productServiceRepository;
    private final Repository orderServiceRepository;
    private final Repository inventoryServiceRepository;

    public EcrStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Product Service Repository
        this.productServiceRepository = new Repository(this, "ProductServiceRepository", RepositoryProps.builder()
                .repositoryName("kong-microservices-ref-app/product-service")
                .removalPolicy(RemovalPolicy.DESTROY)
                .imageScanOnPush(true)
                .imageTagMutability(TagMutability.MUTABLE)
                .lifecycleRules(java.util.Arrays.asList(
                        LifecycleRule.builder()
                                .maxImageCount(10)
                                .build()
                ))
                .build());

        // Order Service Repository
        this.orderServiceRepository = new Repository(this, "OrderServiceRepository", RepositoryProps.builder()
                .repositoryName("kong-microservices-ref-app/order-service")
                .removalPolicy(RemovalPolicy.DESTROY)
                .imageScanOnPush(true)
                .imageTagMutability(TagMutability.MUTABLE)
                .lifecycleRules(java.util.Arrays.asList(
                        LifecycleRule.builder()
                                .maxImageCount(10)
                                .build()
                ))
                .build());

        // Inventory Service Repository
        this.inventoryServiceRepository = new Repository(this, "InventoryServiceRepository", RepositoryProps.builder()
                .repositoryName("kong-microservices-ref-app/inventory-service")
                .removalPolicy(RemovalPolicy.DESTROY)
                .imageScanOnPush(true)
                .imageTagMutability(TagMutability.MUTABLE)
                .lifecycleRules(java.util.Arrays.asList(
                        LifecycleRule.builder()
                                .maxImageCount(10)
                                .build()
                ))
                .build());

        // Outputs
        new CfnOutput(this, "ProductServiceRepositoryUri", CfnOutputProps.builder()
                .value(productServiceRepository.getRepositoryUri())
                .description("Product Service ECR Repository URI")
                .build());

        new CfnOutput(this, "OrderServiceRepositoryUri", CfnOutputProps.builder()
                .value(orderServiceRepository.getRepositoryUri())
                .description("Order Service ECR Repository URI")
                .build());

        new CfnOutput(this, "InventoryServiceRepositoryUri", CfnOutputProps.builder()
                .value(inventoryServiceRepository.getRepositoryUri())
                .description("Inventory Service ECR Repository URI")
                .build());

        new CfnOutput(this, "EcrPushCommand", CfnOutputProps.builder()
                .value("docker tag <image> " + productServiceRepository.getRepositoryUri() + ":latest && docker push " + productServiceRepository.getRepositoryUri() + ":latest")
                .description("Example docker push command")
                .build());
    }

    public Repository getProductServiceRepository() {
        return productServiceRepository;
    }

    public Repository getOrderServiceRepository() {
        return orderServiceRepository;
    }

    public Repository getInventoryServiceRepository() {
        return inventoryServiceRepository;
    }
}
