package com.lab.infra.stacks;

import software.amazon.awscdk.services.eks.*;
import lombok.*;
import software.amazon.awscdk.services.eks.HelmChart; 

@Builder
@Getter
@Setter
public class MicroservicesStackProps {
    private Cluster cluster;
    private String namespace;
    private HelmChart albControllerChart;
    private String productServiceDbHost;
    private int productServiceDbPort;
    private String productServiceDbName;
    private String productServiceImageUri;
    private String orderServiceDbHost;
    private int orderServiceDbPort;
    private String orderServiceDbName;
    private String orderServiceImageUri;
    private String inventoryServiceDbHost;
    private int inventoryServiceDbPort;
    private String inventoryServiceDbName;
    private String inventoryServiceImageUri;
    
}
