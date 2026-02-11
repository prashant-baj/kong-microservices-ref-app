# AWS CDK Infrastructure for Kong Microservices

## Java-based AWS CDK Infrastructure

This directory contains the Infrastructure-as-Code for deploying Kong API Gateway and microservices on AWS EKS with Fargate using Java CDK.

### Quick Start

```bash
# 1. Build the project
mvn clean install

# 2. Bootstrap CDK (one-time setup)
mvn exec:java -Dexec.mainClass="com.lab.infra.InfraApp" -Dexec.args="bootstrap"

# 3. Deploy infrastructure
mvn exec:java -Dexec.mainClass="com.lab.infra.InfraApp" -Dexec.args="deploy"

# 4. Verify deployment
kubectl get pods -A
```

### Prerequisites

- Java 11+
- Maven 3.6+
- AWS CLI v2 (configured)
- kubectl
- Docker (for building services)

### Architecture

**Stacks:**
- **NetworkingStack** - VPC, subnets, security groups
- **EksStack** - EKS cluster with Fargate profiles
- **DatabaseStack** - PostgreSQL running as Kubernetes StatefulSet
- **KongStack** - Kong API Gateway via Helm
- **MicroservicesStack** - Product, Order, Inventory services

### Cost Estimate (2 hours)

- EKS Control Plane: $0.20
- Fargate Compute: $0.28
- Load Balancer: $0.13
- Storage: $0.02
- **Total: ~$0.63**

### Directory Structure

```
infra/
├── src/main/java/com/lab/infra/
│   ├── InfraApp.java
│   └── stacks/
│       ├── NetworkingStack.java
│       ├── EksStack.java
│       ├── EksStackProps.java
│       ├── DatabaseStack.java
│       ├── DatabaseStackProps.java
│       ├── KongStack.java
│       ├── KongStackProps.java
│       ├── MicroservicesStack.java
│       └── MicroservicesStackProps.java
├── pom.xml
└── README.md
```

### Customization

**Change region:**
```bash
export AWS_REGION=us-east-1
mvn exec:java -Dexec.mainClass="com.lab.infra.InfraApp"
```

**Change instance sizes:**
Edit `DatabaseStack.java` and modify resource limits in container specs.

**Scale services:**
Edit `MicroservicesStack.java` and change `replicas` value.

### Cleanup

```bash
mvn exec:java -Dexec.mainClass="com.lab.infra.InfraApp" -Dexec.args="destroy"
```

### Troubleshooting

**Maven compilation errors:**
- Ensure Java 11+ is installed: `java -version`
- Update Maven: `mvn -v`

**AWS CLI not found:**
- Install AWS CLI v2: https://aws.amazon.com/cli/
- Configure credentials: `aws configure`

**kubectl not found:**
- Install kubectl: https://kubernetes.io/docs/tasks/tools/

### Support

For issues or questions, check:
- [AWS CDK Java Guide](https://docs.aws.amazon.com/cdk/latest/guide/languages.html)
- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kong Documentation](https://docs.konghq.com/)

---

**All infrastructure is defined in Java. Happy deploying!**
