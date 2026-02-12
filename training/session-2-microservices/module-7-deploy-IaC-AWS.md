# Module 7: Infrastructure as Code (IaC) - Deploying Kong Microservices on AWS

## Table of Contents
1. [Overview](#overview)
2. [Architecture & Stack Components](#architecture--stack-components)
3. [Prerequisites & Environment Setup](#prerequisites--environment-setup)
4. [Understanding the IaC Implementation](#understanding-the-iac-implementation)
5. [Deployment Guide](#deployment-guide)
6. [Post-Deployment Verification](#post-deployment-verification)
7. [Cost Management & Cleanup](#cost-management--cleanup)

---

## Overview

This module explores the **Infrastructure as Code (IaC)** implementation for deploying the Kong API Gateway and microservices stack on **AWS EKS (Elastic Kubernetes Service)** using **AWS CDK (Cloud Development Kit) with Java**.

### Why IaC?

Instead of manually clicking through the AWS Console, IaC allows you to:
- **Version control** your infrastructure
- **Reproduce** environments consistently
- **Automate** deployments and scale quickly
- **Track changes** and maintain audit trails
- **Enable collaboration** across teams

### Technology Stack

- **AWS CDK 2.x** - Infrastructure definition in Java
- **AWS EKS** - Managed Kubernetes cluster
- **AWS Fargate** - Serverless compute for Kubernetes
- **AWS ECR** - Container image registry
- **Kubernetes 1.28** - Container orchestration
- **Kong API Gateway** - API Gateway (Helm deployed)
- **PostgreSQL** - Relational database

---

## Architecture & Stack Components

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      AWS Account                             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    VPC (10.0.0.0/16)                  │  │
│  │  ┌──────────────────────────────────────────────────┐ │  │
│  │  │           EKS Cluster (Fargate)                  │ │  │
│  │  │                                                   │ │  │
│  │  │  ┌─────────────────────────────────────────────┐ │ │  │
│  │  │  │ Kong Namespace                              │ │ │  │
│  │  │  │  ├─ kong-proxy (LoadBalancer - port 80/443)│ │ │  │
│  │  │  │  └─ kong-admin (ClusterIP - port 8001)     │ │ │  │
│  │  │  └─────────────────────────────────────────────┘ │ │  │
│  │  │                                                   │ │  │
│  │  │  ┌─────────────────────────────────────────────┐ │ │  │
│  │  │  │ Microservices Namespace                     │ │ │  │
│  │  │  │  ├─ product-service (port 8081)            │ │ │  │
│  │  │  │  ├─ inventory-service (port 8082)          │ │ │  │
│  │  │  │  └─ order-service (port 8083)              │ │ │  │
│  │  │  └─────────────────────────────────────────────┘ │ │  │
│  │  │                                                   │ │  │
│  │  │  ┌─────────────────────────────────────────────┐ │ │  │
│  │  │  │ Databases Namespace                         │ │ │  │
│  │  │  │  └─ PostgreSQL StatefulSet (port 5432)     │ │ │  │
│  │  │  └─────────────────────────────────────────────┘ │ │  │
│  │  │                                                   │ │  │
│  │  │  ┌─────────────────────────────────────────────┐ │ │  │
│  │  │  │ kube-system (ALB Controller, Metrics)       │ │ │  │
│  │  │  └─────────────────────────────────────────────┘ │ │  │
│  │  └──────────────────────────────────────────────────┘ │  │
│  │                                                        │  │
│  │  Fargate Profiles (Auto-scaling):                     │  │
│  │  ├─ KongFargateProfile (namespace: kong)             │  │
│  │  ├─ MicroservicesFargateProfile (namespace: ms)      │  │
│  │  ├─ DatabasesFargateProfile (namespace: db)          │  │
│  │  └─ CoreSystemFargateProfile (kube-system)           │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              AWS ECR (Container Registry)             │  │
│  │  ├─ product-service                                  │  │
│  │  ├─ inventory-service                                │  │
│  │  └─ order-service                                    │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Stack Components Breakdown

#### 1. **NetworkingStack**
Creates the foundational networking infrastructure:

| Component | Details |
|-----------|---------|
| **VPC** | CIDR: 10.0.0.0/16, spans 3 Availability Zones (AZs) |
| **Subnets** | Public (24), Private with NAT (24), Private Isolated (24) |
| **NAT Gateways** | 1 shared across all AZs for egress traffic |
| **EKS Security Group** | Allows HTTP (80), HTTPS (443) inbound from anywhere |
| **Database Security Group** | Allows PostgreSQL (5432) from EKS security group only |
| **Route Tables** | Public → Internet Gateway, Private → NAT Gateway |

**Key Files:**
- `NetworkingStack.java` - VPC and security group definitions

---

#### 2. **EcrStack**
Creates AWS ECR repositories for container images:

| Repository | Purpose | Image Retention |
|------------|---------|-----------------|
| `kong-microservices-ref-app/product-service` | Product microservice image | Last 10 images |
| `kong-microservices-ref-app/order-service` | Order microservice image | Last 10 images |
| `kong-microservices-ref-app/inventory-service` | Inventory microservice image | Last 10 images |

**Features:**
- Image scanning on push enabled
- Lifecycle policies to prevent registry bloat
- Mutable tags for development flexibility

**Key Files:**
- `EcrStack.java` - ECR repository definitions

---

#### 3. **EksStack**
Creates the EKS Kubernetes cluster with Fargate compute:

| Component | Configuration |
|-----------|----------------|
| **Kubernetes Version** | 1.28 (supported through 2026) |
| **Compute Model** | AWS Fargate (serverless, no EC2 nodes) |
| **Default Capacity** | 0 nodes (Fargate only) |
| **Endpoint Access** | PUBLIC_AND_PRIVATE (secure + accessible) |
| **Kubectl Layer** | Lambda layer with kubectl 1.28 binary |
| **Load Balancer Controller** | AWS ALB Ingress Controller (Helm) |

**Fargate Profiles Created:**
```
├─ KongFargateProfile
│  └─ Matches namespace: "kong"
├─ MicroservicesFargateProfile
│  └─ Matches namespace: "microservices"
├─ DatabasesFargateProfile
│  └─ Matches namespace: "databases"
└─ CoreSystemFargateProfile
   └─ Matches namespaces: "kube-system", "kube-public"
```

**Key Features:**
- IAM roles for service accounts (IRSA) for secure pod authentication
- AWS ALB Ingress Controller for LoadBalancer services
- CloudWatch integration for logs and metrics

**Key Files:**
- `EksStack.java` - Cluster and Fargate profile configuration
- `EksStackProps.java` - Stack properties interface

---

#### 4. **DatabaseStack**
Deploys PostgreSQL as a Kubernetes StatefulSet:

| Component | Specification |
|-----------|--------------|
| **Type** | StatefulSet (1 replica with stable identity) |
| **Storage** | EBS volumes for persistence |
| **Service** | Headless service for stable DNS: `postgres.databases.svc.cluster.local` |
| **Port** | 5432 (PostgreSQL standard) |
| **Init Script** | Automated database creation (product_db, order_db, inventory_db) |

**Databases Created:**
- `product_db` - Product service data
- `order_db` - Order service data
- `inventory_db` - Inventory service data

**Health & Monitoring:**
- Liveness probe: Checks PostgreSQL connectivity every 30 seconds
- Readiness probe: Validates database is accepting connections
- Resource limits: 500m CPU, 512Mi memory

**Key Files:**
- `DatabaseStack.java` - PostgreSQL StatefulSet and initialization
- `DatabaseStackProps.java` - Database configuration interface

---

#### 5. **KongStack**
Deploys Kong API Gateway via Helm:

| Configuration | Value |
|---------------|-------|
| **Deployment Method** | Helm Chart from `https://charts.konghq.com` |
| **Mode** | DB-less (YAML-based configuration) |
| **Namespace** | `kong` |
| **Replicas** | 2 (for HA) |
| **Proxy Service** | LoadBalancer (AWS ALB integration) |
| **Admin Service** | ClusterIP (internal only) |
| **Proxy Ports** | 80 (HTTP), 443 (HTTPS) |
| **Admin Port** | 8001 |

**Resource Allocation:**
- Requests: 250m CPU, 256Mi memory
- Limits: 500m CPU, 512Mi memory

**Key Features:**
- DB-less configuration via ConfigMaps
- RBAC enabled
- Prometheus metrics enabled
- Helm chart auto-updates from Kong repository

**Key Files:**
- `KongStack.java` - Kong Helm deployment

---

#### 6. **MicroservicesStack**
Deploys three microservices (Product, Inventory, Order):

| Service | Port | Database | Replicas |
|---------|------|----------|----------|
| **product-service** | 8081 | product_db | 2 |
| **inventory-service** | 8082 | inventory_db | 2 |
| **order-service** | 8083 | order_db | 2 |

**Deployment Resources per Service:**
- Requests: 200m CPU, 256Mi memory
- Limits: 500m CPU, 512Mi memory

**Networking:**
- Each service has a Kubernetes Deployment + Service + Ingress
- Ingress rules route to Kong API Gateway
- Services discover each other via Kubernetes DNS

**Environment Variables Injected:**
```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.databases.svc.cluster.local:5432/{DB_NAME}
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
LOGGING_LEVEL_COM_LAB=DEBUG
```

**Key Files:**
- `MicroservicesStack.java` - Service deployments, configurations
- `MicroservicesStackProps.java` - Service configuration parameters

---

### Stack Dependencies & Deployment Order

```
InfraApp (Main Entry Point)
  │
  ├─ NetworkingStack (No dependencies)
  │   └─ Outputs: VPC, Security Groups
  │
  ├─ EcrStack (No dependencies)
  │   └─ Outputs: ECR Repository URIs
  │
  ├─ EksStack (Depends on: NetworkingStack)
  │   └─ Outputs: EKS Cluster, ALB Controller Chart
  │
  ├─ DatabaseStack (Depends on: EksStack)
  │   └─ Outputs: PostgreSQL endpoint
  │
  ├─ KongStack (Depends on: EksStack)
  │   └─ Outputs: Kong services
  │
  └─ MicroservicesStack (Depends on: EksStack, DatabaseStack)
      └─ Outputs: Service endpoints
```

The CDK automatically manages stack ordering and resource dependencies.

---

## Prerequisites & Environment Setup

### Required Software

#### 1. **AWS CLI v2**
```bash
# Install AWS CLI v2
# Windows: https://awscli.amazonaws.com/AWSCLIV2.msi
# macOS: brew install awscli
# Linux: curl "https://awscli.amazonaws.com/awscliv2.zip" -o awscliv2.zip

# Verify installation
aws --version
# AWS CLI 2.x.x

# Configure credentials
aws configure
# Enter Access Key ID
# Enter Secret Access Key
# Enter default region (e.g., ap-southeast-1)
# Enter default output format (json)
```

**AWS Account Requirements:**
- Active AWS account with billing enabled
- IAM user with permissions:
  - EKS full access
  - EC2 full access
  - VPC full access
  - IAM role creation
  - CloudFormation full access
  - ECR full access
  - Load Balancer full access

#### 2. **Java Development Kit (JDK) 17+**
```bash
# Install JDK 17+
# Windows: https://www.oracle.com/java/technologies/downloads/
# macOS: brew install openjdk@17
# Linux: apt-get install openjdk-17-jdk

# Verify installation
java -version
# openjdk version "17.x.x"

# Verify JAVA_HOME is set
echo $JAVA_HOME  # Linux/macOS
echo %JAVA_HOME% # Windows (PowerShell)
```

#### 3. **Maven 3.6+**
```bash
# Install Maven
# Windows: https://maven.apache.org/download.cgi
# macOS: brew install maven
# Linux: apt-get install maven

# Verify installation
mvn --version
# Apache Maven 3.8.1
```

#### 4. **kubectl**
```bash
# Install kubectl
# Windows: choco install kubernetes-cli
# macOS: brew install kubectl
# Linux: curl -LO "https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl"

# Verify installation
kubectl version --client
# Client Version: v1.28.x
```

#### 5. **AWS CDK**
```bash
# Install AWS CDK CLI globally
npm install -g aws-cdk

# Verify installation
cdk --version
# 2.x.x
```

#### 6. **Docker** (Optional - for building services locally)
```bash
# Install Docker Desktop
# Download from https://www.docker.com/products/docker-desktop

# Verify installation
docker --version
# Docker version 24.x.x
```

### Environment Variables

Set these environment variables before deploying:

```bash
# Linux/macOS
export AWS_REGION=ap-southeast-1
export AWS_ACCOUNT_ID=123456789012  # Your actual AWS account ID

# Windows PowerShell
$env:AWS_REGION = "ap-southeast-1"
$env:AWS_ACCOUNT_ID = "123456789012"
```

**Region Selection Tips:**
- Use a region close to your location for lower latency
- Fargate pricing varies by region
- Supported regions: us-east-1, us-west-2, eu-west-1, ap-southeast-1, ap-northeast-1, etc.

### AWS Account Cost Estimate

| Resource | Hourly Cost | Notes |
|----------|-------------|-------|
| EKS Control Plane | $0.10 | Fixed per cluster |
| Fargate Compute | ~$0.10-0.20 | Depends on vCPU/memory allocation |
| ALB Load Balancer | $0.13 | Fixed + per request charges |
| EBS Storage | $0.10/100GB | For PostgreSQL persistence |
| NAT Gateway | ~$0.05 | Data transfer costs apply |
| ECR Storage | ~$0.10 | Per GB stored |
| **Total Hourly** | **~$0.50-0.75** | Estimate only |

**Running for 2 hours:** ~$1.00-1.50

---

## Understanding the IaC Implementation

### Project Structure

```
infra/
├── pom.xml                          # Maven configuration
├── deploy.ps1                       # PowerShell deployment script
├── README_JAVA.md                   # Quick start guide
│
└── src/main/java/com/lab/infra/
    ├── InfraApp.java                # CDK Application entry point
    └── stacks/
        ├── NetworkingStack.java      # VPC and networking
        ├── NetworkingStack.java
        ├── EcrStack.java             # Container registry
        ├── EksStack.java             # Kubernetes cluster
        ├── EksStackProps.java
        ├── DatabaseStack.java        # PostgreSQL deployment
        ├── DatabaseStackProps.java
        ├── KongStack.java            # API Gateway
        ├── KongStackProps.java
        ├── MicroservicesStack.java   # Microservices
        └── MicroservicesStackProps.java
```

### Key Concepts

#### **1. CDK Stacks**
A `Stack` is a unit of deployment. The app contains multiple stacks, each deploying specific AWS resources.

```java
public class NetworkingStack extends Stack {
    public NetworkingStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        // Create VPC and networking resources
    }
}
```

#### **2. Constructs**
Reusable components that represent AWS resources:

```java
Vpc vpc = new Vpc(this, "KongMicroservicesVpc", VpcProps.builder()
    .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
    .maxAzs(3)
    .build());
```

#### **3. Properties & Builders**
Configuration objects passed between stacks:

```java
public class EksStackProps {
    private Vpc vpc;
    private SecurityGroup eksSecurityGroup;
    
    // Builder pattern for immutability
}
```

#### **4. Kubernetes Manifests**
Deploy Kubernetes resources directly from CDK:

```java
Map<String, Object> namespace = Map.of(
    "apiVersion", "v1",
    "kind", "Namespace",
    "metadata", Map.of("name", "databases")
);

KubernetesManifest dbNamespace = cluster.addManifest("DatabaseNamespace", namespace);
```

#### **5. Helm Charts**
Deploy Helm charts for complex applications:

```java
HelmChart.Builder.create(this, "KongHelmChart")
    .cluster(cluster)
    .chart("kong")
    .repository("https://charts.konghq.com")
    .namespace("kong")
    .createNamespace(true)
    .values(createKongValues())
    .build();
```

#### **6. Stack Dependencies**
Ensure correct deployment order:

```java
eksStack.addDependency(networkingStack);
databaseStack.addDependency(eksStack);
kongStack.addDependency(eksStack);
microservicesStack.addDependency(eksStack);
microservicesStack.addDependency(databaseStack);
```

### Configuration Flow

#### **InfraApp.java** - Orchestration
```
1. Read environment variables (AWS_REGION, AWS_ACCOUNT_ID)
2. Create base StackProps with region and account
3. Instantiate all stacks in dependency order
4. Pass outputs from one stack as inputs to dependent stacks
5. Call app.synth() to generate CloudFormation template
```

#### **Example Configuration Chain**
```
InfraApp gets AWS region
    ↓
NetworkingStack creates VPC
    ↓
NetworkingStack.getVpc() → passed to EksStack
    ↓
EksStack uses VPC to create cluster
    ↓
EksStack.getCluster() → passed to DatabaseStack and MicroservicesStack
    ↓
DatabaseStack/MicroservicesStack deploy on the cluster
```

---

## Deployment Guide

### Step 1: Clone and Navigate to Infra Directory

```bash
cd c:\labs\kong-microservices-ref-app\infra
```

### Step 2: Set AWS Region and Account

```powershell
# PowerShell
$env:AWS_REGION = "ap-southeast-1"
$env:AWS_ACCOUNT_ID = "123456789012"  # Replace with your account ID

# Get your AWS account ID:
aws sts get-caller-identity
```

### Step 3: Build the CDK Project

```bash
mvn clean install
```

**Output:**
```
[INFO] Building Kong Microservices Infrastructure as Code 1.0.0-SNAPSHOT
[INFO] ----------------< com.lab:kong-microservices-cdk >-----------------
[INFO] BUILD SUCCESS
```

### Step 4: Bootstrap AWS CDK (One-time Setup)

```bash
cdk bootstrap aws://123456789012/ap-southeast-1
```

**What it does:**
- Creates S3 bucket for CDK artifacts
- Creates IAM roles for CDK deployment
- Creates CloudFormation stack

**Output:**
```
 ✓ CDKToolkit stack created/already exists in ...
 ✓ Bootstrap complete!
```

### Step 5: Deploy Using PowerShell Script

**Option A: Automated Deployment**
```powershell
# Run from infra directory
.\deploy.ps1
```

This script:
1. Builds Maven project
2. Bootstraps CDK
3. Deploys ECR Stack
4. Parses ECR output
5. Builds and pushes microservice images
6. Deploys remaining stacks

**Option B: Manual Deployment**

#### Deploy ECR Stack First
```bash
cdk deploy EcrStack --require-approval never
```

**Output:**
```
EcrStack

Outputs:
EcrStack.ProductServiceRepositoryUri = 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/product-service
EcrStack.OrderServiceRepositoryUri = 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/order-service
EcrStack.InventoryServiceRepositoryUri = 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/inventory-service

✓ Stack deployment time: 25.13s
```

#### Build and Push Container Images

```bash
# Windows
cd ..\services\product-service
docker build -t 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/product-service:latest .

# Push to ECR
aws ecr get-login-password --region ap-southeast-1 | docker login --username AWS --password-stdin 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com
docker push 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/product-service:latest

# Repeat for inventory-service and order-service
```

#### Deploy All Remaining Stacks
```bash
cd ../infra
cdk deploy --all --require-approval never
```

**Total deployment time:** 15-25 minutes

**Deployment Progress:**
```
NetworkingStack: creating...
NetworkingStack: success (5 min)
EksStack: creating... (Fargate cluster setup)
EksStack: success (12 min)
DatabaseStack: creating... (PostgreSQL on K8s)
DatabaseStack: success (2 min)
KongStack: creating... (Kong Helm chart)
KongStack: success (3 min)
MicroservicesStack: creating... (Deployments)
MicroservicesStack: success (2 min)

✓ All stacks deployed successfully!
```

### Step 6: Configure kubectl Context

```bash
# Update kubeconfig
aws eks update-kubeconfig --name kong-microservices-cluster --region ap-southeast-1

# Verify connection
kubectl get nodes
# No nodes (Fargate uses managed pools, not visible as nodes)

kubectl get pods -A
# All pods running on Fargate
```

---

## Post-Deployment Verification

### 1. Check All Pods Are Running

```bash
# All pods across all namespaces
kubectl get pods -A

# Expected output:
NAMESPACE         NAME                                      READY   STATUS
databases         postgres-0                                1/1     Running
kong              kong-kong-6784d4847-xxxxx                 1/1     Running
kong              kong-kong-6784d4847-yyyyy                 1/1     Running
microservices     product-service-7854b4c89c-xxxxx         1/1     Running
microservices     product-service-7854b4c89c-yyyyy         1/1     Running
microservices     inventory-service-5f84c4d7bd-xxxxx       1/1     Running
microservices     inventory-service-5f84c4d7bd-yyyyy       1/1     Running
microservices     order-service-6c84d4e89d-xxxxx           1/1     Running
microservices     order-service-6c84d4e89d-yyyyy           1/1     Running
kube-system       aws-load-balancer-controller-8xxx         1/1     Running
kube-system       metrics-server-xxxxx                      1/1     Running
```

### 2. Verify Service Connectivity

```bash
# Check services
kubectl get svc -A

# Expected output:
NAMESPACE      NAME              TYPE           CLUSTER-IP    EXTERNAL-IP   PORT(S)
kong           kong-kong-proxy   LoadBalancer   10.100.xxx    xxxxx.elb.    80:xxxxx/TCP,443:xxxxx/TCP
kong           kong-kong-admin   ClusterIP      10.100.xxx    <none>        8001/TCP
microservices  product-service   LoadBalancer   10.100.xxx    xxxxx.elb.    8081:xxxxx/TCP
microservices  inventory-service LoadBalancer   10.100.xxx    xxxxx.elb.    8082:xxxxx/TCP
microservices  order-service     LoadBalancer   10.100.xxx    xxxxx.elb.    8083:xxxxx/TCP
databases      postgres          ClusterIP      None          <none>        5432/TCP
```

### 3. Get Kong Proxy URL

```bash
# Get Kong LoadBalancer URL
kubectl get svc -n kong kong-kong-proxy -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'

# Example output:
# kong-nlb-xxxxxxxxxx-xxxxxxxx.elb.ap-southeast-1.amazonaws.com

# Save for later use
export KONG_URL="kong-nlb-xxxxxxxxxx-xxxxxxxx.elb.ap-southeast-1.amazonaws.com"
```

### 4. Verify Ingresses

```bash
# Check Ingress resources
kubectl get ingress -A

# Expected output:
NAMESPACE      NAME               CLASS    HOSTS   ADDRESS   PORTS   AGE
microservices  product-service    alb      *       xxx       80      2m
microservices  inventory-service  alb      *       xxx       80      2m
microservices  order-service      alb      *       xxx       80      2m
```

### 5. Check Database Initialization

```bash
# Connect to PostgreSQL pod
kubectl exec -it postgres-0 -n databases -- psql -U postgres -c "\l"

# Expected output:
                                 List of databases
           Name           | Owner    | Encoding | Collate   | Ctype     | Access privileges
--------------------------|----------|----------|-----------|-----------|-------------------
 product_db               | postgres | UTF8     | C         | en_US.... |
 order_db                 | postgres | UTF8     | C         | en_US.... |
 inventory_db             | postgres | UTF8     | C         | en_US.... |
 postgres                 | postgres | UTF8     | C         | en_US.... |

# Check product_db tables
kubectl exec -it postgres-0 -n databases -- psql -U postgres -d product_db -c "\dt"
```

### 6. Test Microservice Connectivity

```bash
# Port-forward to Kong proxy
kubectl port-forward -n kong svc/kong-kong-proxy 8000:80 &

# Test product service via Kong
curl http://localhost:8000/product-service/health
# Expected: {"status":"UP"}

# Test health checks on all services
curl http://localhost:8000/product-service/health
curl http://localhost:8000/inventory-service/health
curl http://localhost:8000/order-service/health
```

### 7. View Logs

```bash
# Kong logs
kubectl logs -n kong -l app.kubernetes.io/name=kong --tail=50

# Microservice logs
kubectl logs -n microservices -l app=product-service --tail=50

# Database logs
kubectl logs -n databases postgres-0 --tail=50
```

### 8. Check CloudWatch Logs (Optional)

```bash
# View EKS cluster logs
aws logs describe-log-groups --region ap-southeast-1 | grep kong-microservices
```

---

## Cost Management & Cleanup

### Monitoring Costs

#### AWS Cost Explorer
1. Navigate to AWS Console → Cost Management → Cost Explorer
2. Filter by service: EKS, EC2 (Fargate), ALB, ECR
3. Set time range to "Last 7 days" for your deployment

#### CloudWatch Metrics
```bash
# EKS Cluster metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/EKS \
  --metric-name ClusterNode \
  --dimensions Name=ClusterName,Value=kong-microservices-cluster \
  --start-time 2024-01-01T00:00:00Z \
  --end-time 2024-01-02T00:00:00Z \
  --period 3600 \
  --statistics Average \
  --region ap-southeast-1
```

### Cost Optimization Tips

1. **Reduce Fargate Replicas** (Development only)
```bash
# Change replicas in MicroservicesStack.java from 2 to 1
# Redeploy with: cdk deploy MicroservicesStack
```

2. **Stop the Cluster** (if not in use)
```bash
# You cannot pause EKS clusters, but you can:
# a) Delete and recreate (using IaC)
# b) Scale Fargate profiles to 0 (requires manual intervention)
```

3. **Use Spot Instances** (if adding EC2 nodes)
- Modify EksStack.java to use Spot instances instead of Fargate
- Cost reduction: 70% vs on-demand

4. **Right-size Resources**
- Monitor actual CPU/memory usage
- Adjust requests and limits in stack definitions

### Cleanup & Destroy Resources

#### Option 1: Destroy All Stacks (Recommended)

```bash
cdk destroy --all --force
```

**Output:**
```
Are you sure? [y/N]: y
EcrStack: destroying... (checking for ECR images)
NetworkingStack: destroying...
EksStack: destroying...
[WARNING] EKS cluster deletion can take 10-15 minutes
...
✓ All stacks destroyed
```

#### Option 2: Destroy Individual Stacks

```bash
# Order matters: dependent stacks first
cdk destroy MicroservicesStack KongStack DatabaseStack EksStack EcrStack NetworkingStack --force
```

#### Option 3: Using CloudFormation Console
1. Navigate to AWS Console → CloudFormation
2. Select each stack (reverse dependency order)
3. Click "Delete"
4. Confirm deletion

### Post-Cleanup Verification

```bash
# Verify all resources are deleted
aws eks describe-clusters --region ap-southeast-1
# Returns: "clusters": []

aws ec2 describe-vpcs --region ap-southeast-1
# Verify no "kong-microservices" VPC exists

aws ecr describe-repositories --region ap-southeast-1
# Verify no ECR repositories exist
```

---

## Troubleshooting

### Common Issues & Solutions

#### Issue: "Maven not found"
```bash
# Windows: Install Maven or use mvnw from project
mvn --version
# If not found, download from https://maven.apache.org/download.cgi
```

#### Issue: "AWS CLI not configured"
```bash
aws configure
# Enter AWS Access Key ID
# Enter AWS Secret Access Key
# Enter default region (e.g., ap-southeast-1)
```

#### Issue: "Bootstrap required" error
```bash
cdk bootstrap aws://123456789012/ap-southeast-1
```

#### Issue: EKS cluster creation timeout (10-15 minutes)
- This is normal. EKS control plane takes time to initialize
- Monitor progress: `aws eks describe-cluster --name kong-microservices-cluster --region ap-southeast-1`

#### Issue: Pods stuck in "Pending"
```bash
# Check Fargate profile status
kubectl describe node

# Check pod events
kubectl describe pod <pod-name> -n <namespace>

# Solution: Ensure Fargate profile exists for the namespace
```

#### Issue: ECR image not found
```bash
# Verify image was pushed
aws ecr describe-images --repository-name kong-microservices-ref-app/product-service --region ap-southeast-1

# Re-push image
docker tag myimage:latest 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/product-service:latest
docker push 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/kong-microservices-ref-app/product-service:latest
```

#### Issue: Database connection failures
```bash
# Test PostgreSQL connectivity
kubectl exec -it postgres-0 -n databases -- psql -U postgres -c "SELECT 1"

# Check pod logs
kubectl logs postgres-0 -n databases

# Verify security groups allow port 5432
aws ec2 describe-security-groups --region ap-southeast-1 | grep 5432
```

#### Issue: High AWS bill after deployment
- Destroy stacks immediately: `cdk destroy --all --force`
- Check CloudFormation for orphaned resources
- Review CloudWatch alarms

---

## Summary

This module covered:

✅ **Architecture Overview** - Multi-tier microservices on AWS EKS
✅ **Stack Components** - 6 CDK stacks with specific responsibilities
✅ **Prerequisites** - Java, Maven, kubectl, AWS CLI setup
✅ **IaC Concepts** - Constructs, stacks, manifests, Helm integration
✅ **Deployment** - Step-by-step guide with verification
✅ **Cost Management** - Monitoring and cleanup procedures

### Key Takeaways

1. **IaC enables reproducibility** - Deploy identical environments consistently
2. **AWS CDK simplifies Kubernetes** - Write infrastructure in Java, not YAML
3. **Fargate reduces complexity** - No EC2 node management needed
4. **Stack dependencies matter** - CDK manages deployment ordering
5. **Always destroy unused resources** - Prevent unexpected AWS bills

### Next Steps

- Monitor the deployed cluster with CloudWatch
- Create additional Kubernetes manifests for custom workloads
- Implement CI/CD pipeline for automatic deployments
- Add networking policies and RBAC for security
- Explore Kubernetes Operators for advanced resource management

---

## References & Resources

- [AWS CDK Documentation](https://docs.aws.amazon.com/cdk/)
- [AWS EKS Documentation](https://docs.aws.amazon.com/eks/)
- [Kubernetes Official Docs](https://kubernetes.io/docs/)
- [Kong Documentation](https://docs.konghq.com/)
- [AWS Fargate Pricing](https://aws.amazon.com/fargate/pricing/)
- [PostgreSQL on Kubernetes Best Practices](https://www.postgresql.org/)
- [Helm Charts Official Repository](https://artifacthub.io/)

---

**Module Status:** Complete ✅

**Estimated Time to Complete:** 3-4 hours (including deployment)

**Prerequisites Met:** Yes - All software installed and configured

**Next Module:** Session 3 - Advanced Kubernetes & Production Readiness
