package com.lab.infra.stacks;

import software.amazon.awscdk.services.eks.*;
import lombok.*;

@Builder
@Getter
@Setter
public class KongStackProps {
    private Cluster cluster;
    private String namespace;
}
