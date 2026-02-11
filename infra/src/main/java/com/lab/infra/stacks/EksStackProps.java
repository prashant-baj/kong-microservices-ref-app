package com.lab.infra.stacks;

import software.amazon.awscdk.services.ec2.*;
import lombok.*;

@Builder
@Getter
@Setter
public class EksStackProps {
    private Vpc vpc;
    private SecurityGroup eksSecurityGroup;
}
