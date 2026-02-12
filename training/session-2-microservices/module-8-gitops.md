# Module 8: GitOps & CI/CD Pipeline Automation

## Table of Contents
1. [Overview](#overview)
2. [GitOps Principles](#gitops-principles)
3. [CI/CD Pipeline Architecture](#cicd-pipeline-architecture)
4. [Pipeline Stages](#pipeline-stages)
5. [Implementation Details](#implementation-details)
6. [Infrastructure Drift Detection](#infrastructure-drift-detection)
7. [Monitoring & Observability](#monitoring--observability)
8. [Security & Best Practices](#security--best-practices)

---

## Overview

### What is GitOps?

GitOps is a methodology that uses **Git as the single source of truth** for both application code and infrastructure. Instead of manually deploying changes, all changes flow through Git:

```
Developer → Git Push → Git Repository → Pipeline Automation → Live Environment
```

### Benefits of GitOps

| Benefit | Description |
|---------|-------------|
| **Auditability** | Every change tracked in Git with commit history |
| **Reproducibility** | Redeploy any version by checking out a specific commit |
| **Safety** | Pull requests enable code reviews before deployment |
| **Automation** | Eliminates manual, error-prone steps |
| **Disaster Recovery** | Infrastructure can be recreated from Git state |
| **Compliance** | Full audit trail for regulatory requirements |

### Modern CI/CD Stack

For the Kong Microservices platform, we recommend:

| Component | Tool | Purpose |
|-----------|------|---------|
| **Version Control** | GitHub | Repository for code and IaC |
| **CI Pipeline** | GitHub Actions | Automated testing and builds |
| **CD Pipeline** | ArgoCD | GitOps deployment orchestration |
| **Container Registry** | AWS ECR | Store and manage container images |
| **Infrastructure** | AWS CDK + Terraform | IaC for AWS resources |
| **Monitoring** | Prometheus + Grafana | Observe pipeline and infrastructure |

---

## GitOps Principles

### 1. Git as Source of Truth

```
Repository Structure:
kong-microservices-ref-app/
├── code/                          # Application source code
│   ├── services/
│   │   ├── product-service/
│   │   ├── order-service/
│   │   └── inventory-service/
│   └── pom.xml
├── infra/                         # Infrastructure as Code
│   ├── src/main/java/
│   ├── pom.xml
│   └── deploy.ps1
├── k8s/                           # Kubernetes manifests
│   ├── kong/
│   ├── services/
│   └── postgres/
├── .github/workflows/             # GitHub Actions CI/CD
│   ├── ci.yml
│   ├── build-images.yml
│   ├── deploy-infra.yml
│   └── drift-detection.yml
└── README.md
```

**Key Principle:** Everything needed to reproduce the system is in Git.

### 2. Declarative Configuration

Describe the **desired state**, not the **steps to achieve it**:

```yaml
# Declarative (Good) - Kubernetes Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: product-service
  template:
    spec:
      containers:
      - name: product-service
        image: 123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/product-service:v1.2.3
        ports:
        - containerPort: 8081

# NOT Imperative (Bad) - Manual steps
# 1. SSH into server
# 2. Kill old process
# 3. Download new version
# 4. Start new process
```

### 3. Automated Reconciliation

Systems continuously compare **desired state** (in Git) with **actual state** (in cluster):

```
Git State: "product-service should have 2 replicas"
    ↓
ArgoCD Checks Every 3 Minutes
    ↓
Actual State: "product-service has 1 replica"
    ↓
DRIFT DETECTED
    ↓
ArgoCD Auto-Syncs: "Scaling to 2 replicas"
    ↓
Desired == Actual: "Reconciliation Complete"
```

---

## CI/CD Pipeline Architecture

### Complete Pipeline Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         DEVELOPER WORKFLOW                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  Developer writes code → Commits → Pushes to GitHub → Creates Pull Request    │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                      ① PULL REQUEST VALIDATION (Automated)                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ GitHub Actions Triggered: PR Checks                                    │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │  • Code Linting (CheckStyle, SpotBugs)          ✓ PASS/FAIL            │   │
│  │  • Unit Tests (JUnit, Mockito)                  ✓ Code Coverage        │   │
│  │  • SonarQube Static Analysis                     ✓ Code Quality         │   │
│  │  • Docker Build (Services)                       ✓ Image Build          │   │
│  │  • Integration Tests (REST-assured)              ✓ E2E Tests            │   │
│  │  • SAST Security Scan (Trivy, Sonarqube)         ✓ Vulnerabilities      │   │
│  │  • IaC Validation (Terraform Plan)               ✓ Infrastructure Check │   │
│  │  • Cost Analysis (Infracost)                     ✓ Budget Impact        │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  All Checks Pass? → PR Approved → Code Review → Merge to Main                │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                   ② BUILD & PUSH TO REGISTRY (Automated)                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────────────┐                                                  │
│  │ Commit to main triggered │                                                  │
│  └──────────────────────────┘                                                  │
│                  ↓                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ Build Stage                                                            │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │  ✓ Maven build all services                                           │   │
│  │  ✓ Build container images for each service                           │   │
│  │  ✓ Tag images: :latest, :v1.2.3, :sha-abc123                        │   │
│  │  ✓ Push to AWS ECR                                                   │   │
│  │  ✓ Scan images for vulnerabilities (Trivy)                          │   │
│  │  ✓ Update image digests in manifests                                │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│              ③ DEPLOY INFRASTRUCTURE & APPLICATIONS (Automated)                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────────────────┐        ┌──────────────────────────────┐      │
│  │ IaC Deployment               │        │ Kubernetes Deployment        │      │
│  ├──────────────────────────────┤        ├──────────────────────────────┤      │
│  │ AWS CDK Deploy:              │        │ ArgoCD Sync:                 │      │
│  │  ✓ VPC & Networking          │        │  ✓ Kong API Gateway         │      │
│  │  ✓ EKS Cluster               │        │  ✓ Microservices            │      │
│  │  ✓ ECR Repositories          │        │  ✓ PostgreSQL StatefulSet   │      │
│  │  ✓ Security Groups           │        │  ✓ ConfigMaps & Secrets     │      │
│  │  ✓ Load Balancers            │        │  ✓ RBAC & NetworkPolicies   │      │
│  │  ✓ Monitoring (CloudWatch)   │        │  ✓ Ingress & Services       │      │
│  └──────────────────────────────┘        └──────────────────────────────┘      │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                    ④ SMOKE TESTS & VERIFICATION (Automated)                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ Health & Readiness Checks                                             │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │  ✓ Wait for all pods: Running (timeout: 5 min)                       │   │
│  │  ✓ Check service endpoints: Responsive                               │   │
│  │  ✓ Database connectivity: Verified                                   │   │
│  │  ✓ API health endpoints: /health responses                           │   │
│  │  ✓ E2E integration test: Full order flow test                        │   │
│  │  ✓ Performance baseline: Response times                              │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                ⑤ INFRASTRUCTURE DRIFT DETECTION (Scheduled)                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ Drift Detection (Runs every 6 hours)                                  │   │
│  ├─────────────────────────────────────────────────────────────────────────┤   │
│  │  ✓ Compare Git state vs Actual AWS state                             │   │
│  │  ✓ Compare Git state vs Actual K8s state                             │   │
│  │  ✓ Report drift findings                                             │   │
│  │  ✓ Alert on critical drift (security groups, RBAC)                  │   │
│  │  ✓ Auto-remediate minor drift (scale, tags)                         │   │
│  │  ✓ Create incident tickets for manual review                        │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│                              ↓↓↓                                               │
├─────────────────────────────────────────────────────────────────────────────────┤
│                    ⑥ MONITORING & ALERTING (Continuous)                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────────────────┐        ┌──────────────────────────────┐      │
│  │ Infrastructure Metrics       │        │ Application Metrics          │      │
│  ├──────────────────────────────┤        ├──────────────────────────────┤      │
│  │  • EKS cluster health        │        │  • Request latency           │      │
│  │  • Pod CPU/Memory usage      │        │  • Error rates               │      │
│  │  • Node availability         │        │  • Throughput               │      │
│  │  • EBS volume utilization    │        │  • Database connection pool  │      │
│  │  • Network I/O               │        │  • Cache hit rates           │      │
│  │  • Cost tracking             │        │  • Custom business metrics   │      │
│  └──────────────────────────────┘        └──────────────────────────────┘      │
│                                                                                 │
│  Dashboards: Prometheus + Grafana                                             │
│  Alerts: PagerDuty integration for on-call notifications                      │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Pipeline Stages

### Stage 1: Pull Request Validation

**Trigger:** Git push to feature branch, create/update pull request

#### 1.1 Code Quality Checks

```yaml
# .github/workflows/ci.yml
name: PR Validation - Code Quality

on:
  pull_request:
    paths:
      - 'services/**'
      - 'infra/**'

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run Checkstyle
        run: mvn checkstyle:check
      
      - name: Run SpotBugs
        run: mvn spotbugs:check
```

**Tools Used:**
- **Checkstyle** - Code style and formatting
- **SpotBugs** - Find common Java bugs
- **PMD** - Code defects and design issues
- **Sonarqube** - Overall code quality

#### 1.2 Unit Tests & Coverage

```yaml
name: PR Validation - Unit Tests

on: [pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run Unit Tests
        run: mvn test
      
      - name: Generate Coverage Report
        run: mvn jacoco:report
      
      - name: Upload to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./target/site/jacoco/jacoco.xml
          flags: unittests
          fail_ci_if_error: true
          minimum-coverage: 70  # Enforce minimum coverage
      
      - name: Comment Coverage on PR
        uses: romeovs/lcov-reporter-action@v0.3.1
        if: always()
        with:
          lcov-file: ./target/site/jacoco/jacoco.csv
```

**Coverage Thresholds:**
| Metric | Minimum | Target |
|--------|---------|--------|
| Line Coverage | 70% | 80% |
| Branch Coverage | 60% | 75% |
| Method Coverage | 70% | 85% |
| Class Coverage | 85% | 95% |

#### 1.3 Build & Scan Images

```yaml
name: PR Validation - Build & Scan

on: [pull_request]

jobs:
  build-scan:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [product-service, order-service, inventory-service]
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: |
          cd services/${{ matrix.service }}
          docker build -t ${{ matrix.service }}:${{ github.sha }} .
      
      - name: Scan image with Trivy
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ${{ matrix.service }}:${{ github.sha }}
          format: 'sarif'
          output: 'trivy-results.sarif'
      
      - name: Upload Trivy results
        uses: github/codeql-action/upload-sarif@v2
        with:
          sarif_file: 'trivy-results.sarif'
      
      - name: Fail on Critical Vulnerabilities
        run: |
          # If Trivy found CRITICAL vulnerabilities, fail
          if grep -q "CRITICAL" trivy-results.sarif; then
            echo "Critical vulnerabilities found!"
            exit 1
          fi
```

**Vulnerability Scanning:**
- Scans for known CVEs in dependencies
- Fails build on CRITICAL severity
- Fails build on HIGH if exploitable
- Allows LOW/MEDIUM with review

#### 1.4 Integration Tests

```yaml
name: PR Validation - Integration Tests

on: [pull_request]

jobs:
  integration-tests:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Run Integration Tests
        run: |
          cd integration-tests
          mvn verify -Dgateway.url=http://localhost:8080
        env:
          DB_HOST: localhost
          DB_PORT: 5432
          DB_USER: postgres
          DB_PASSWORD: postgres
```

#### 1.5 IaC Validation

```yaml
name: PR Validation - Infrastructure as Code

on: [pull_request]

jobs:
  iac-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Validate CDK Stack
        run: |
          cd infra
          mvn clean install
          cdk synth  # Generate CloudFormation template
      
      - name: Scan CloudFormation with Checkov
        uses: bridgecrewio/checkov-action@master
        with:
          directory: infra/cdk.out
          framework: cloudformation
          compact: true
      
      - name: Cost Analysis (Infracost)
        uses: infracost/actions/setup@v2
        with:
          api-key: ${{ secrets.INFRACOST_API_KEY }}
      
      - name: Generate Cost Report
        run: |
          cd infra
          infracost breakdown --terraform-plan-file cdk.out
```

---

### Stage 2: Build & Push to Registry

**Trigger:** Merge to main branch

```yaml
name: Build & Push Images

on:
  push:
    branches: [main]
    paths:
      - 'services/**'

env:
  AWS_REGION: ap-southeast-1
  AWS_ACCOUNT_ID: ${{ secrets.AWS_ACCOUNT_ID }}

jobs:
  build-push:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: [product-service, order-service, inventory-service]
    
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Generate image tags
        id: meta
        run: |
          echo "VERSION=$(git describe --tags --always)" >> $GITHUB_OUTPUT
          echo "SHA=$(git rev-parse --short HEAD)" >> $GITHUB_OUTPUT
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::${{ env.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to AWS ECR
        run: |
          aws ecr get-login-password --region ${{ env.AWS_REGION }} | \
          docker login --username AWS --password-stdin \
          ${{ env.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com
      
      - name: Build and push image
        run: |
          cd services/${{ matrix.service }}
          
          IMAGE_URI="${{ env.AWS_ACCOUNT_ID }}.dkr.ecr.${{ env.AWS_REGION }}.amazonaws.com/kong-microservices-ref-app/${{ matrix.service }}"
          
          docker build \
            --tag ${IMAGE_URI}:latest \
            --tag ${IMAGE_URI}:${{ steps.meta.outputs.VERSION }} \
            --tag ${IMAGE_URI}:${{ steps.meta.outputs.SHA }} \
            .
          
          docker push ${IMAGE_URI}:latest
          docker push ${IMAGE_URI}:${{ steps.meta.outputs.VERSION }}
          docker push ${IMAGE_URI}:${{ steps.meta.outputs.SHA }}
          
          echo "IMAGE_URI=${IMAGE_URI}" >> $GITHUB_ENV
          echo "IMAGE_SHA=${{ steps.meta.outputs.SHA }}" >> $GITHUB_ENV
      
      - name: Update deployment manifests
        run: |
          # Update k8s manifests with new image digest
          sed -i "s|IMAGE_URI|${{ env.IMAGE_URI }}|g" k8s/services/${{ matrix.service }}/deployment.yaml
          sed -i "s|IMAGE_SHA|${{ env.IMAGE_SHA }}|g" k8s/services/${{ matrix.service }}/deployment.yaml
      
      - name: Commit updated manifests
        run: |
          git config user.email "actions@github.com"
          git config user.name "GitHub Actions"
          git add k8s/
          git commit -m "chore: update image digest for ${{ matrix.service }}"
          git push origin main
```

---

### Stage 3: Deploy Infrastructure

**Trigger:** Changes to infra/ or on schedule (daily)

```yaml
name: Deploy Infrastructure

on:
  push:
    branches: [main]
    paths:
      - 'infra/**'
  schedule:
    - cron: '0 2 * * *'  # 2 AM daily

env:
  AWS_REGION: ap-southeast-1
  AWS_ACCOUNT_ID: ${{ secrets.AWS_ACCOUNT_ID }}

jobs:
  deploy:
    runs-on: ubuntu-latest
    
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      - name: Install AWS CDK
        run: npm install -g aws-cdk
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::${{ env.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Build CDK
        run: |
          cd infra
          mvn clean install
      
      - name: Diff infrastructure
        id: diff
        run: |
          cd infra
          cdk diff 2>&1 | tee diff.txt
          
          # If diff is empty, no changes
          if [ ! -s diff.txt ] || grep -q "There are no differences" diff.txt; then
            echo "CHANGES=false" >> $GITHUB_OUTPUT
          else
            echo "CHANGES=true" >> $GITHUB_OUTPUT
          fi
      
      - name: Comment CDK diff on PR
        if: github.event_name == 'pull_request' && steps.diff.outputs.CHANGES == 'true'
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const diff = fs.readFileSync('infra/diff.txt', 'utf8');
            github.rest.issues.createComment({
              issue_number: context.issue.number,
              owner: context.repo.owner,
              repo: context.repo.repo,
              body: '```\n' + diff + '\n```'
            });
      
      - name: Deploy infrastructure
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        run: |
          cd infra
          cdk deploy --all --require-approval never
      
      - name: Save outputs
        if: github.ref == 'refs/heads/main'
        run: |
          cd infra
          cdk output --all > infrastructure-outputs.json
      
      - name: Upload outputs artifact
        uses: actions/upload-artifact@v3
        with:
          name: infrastructure-outputs
          path: infra/infrastructure-outputs.json
```

---

### Stage 4: Deploy Applications (GitOps)

**Trigger:** Image push to ECR (detected by ArgoCD)

#### ArgoCD Application Deployment

```yaml
# k8s/argocd/kong-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: kong-api-gateway
  namespace: argocd
spec:
  project: default
  
  source:
    repoURL: https://github.com/your-org/kong-microservices-ref-app.git
    targetRevision: main
    path: k8s/kong
  
  destination:
    server: https://kubernetes.default.svc
    namespace: kong
  
  syncPolicy:
    automated:
      prune: true      # Delete resources removed from Git
      selfHeal: true   # Sync when cluster drifts
      allowEmpty: false
    
    syncOptions:
      - CreateNamespace=true
    
    retry:
      limit: 5
      backoff:
        duration: 5s
        factor: 2
        maxDuration: 3m
  
  revisionHistoryLimit: 10

---
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: microservices
  namespace: argocd
spec:
  project: default
  
  source:
    repoURL: https://github.com/your-org/kong-microservices-ref-app.git
    targetRevision: main
    path: k8s/services
    
    # Kustomize for managing multiple service deployments
    kustomize:
      commonLabels:
        app.kubernetes.io/version: "1.0.0"
      
      images:
        - name: product-service
          newTag: ${PRODUCT_SERVICE_TAG}
        - name: order-service
          newTag: ${ORDER_SERVICE_TAG}
        - name: inventory-service
          newTag: ${INVENTORY_SERVICE_TAG}
  
  destination:
    server: https://kubernetes.default.svc
    namespace: microservices
  
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    
    syncOptions:
      - CreateNamespace=true
      - PrunePropagationPolicy=foreground
      - PruneLast=true
```

#### ArgoCD Sync Workflow

```yaml
name: ArgoCD Sync

on:
  push:
    branches: [main]
    paths:
      - 'k8s/**'

jobs:
  sync:
    runs-on: ubuntu-latest
    
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: ap-southeast-1
      
      - name: Update kubeconfig
        run: |
          aws eks update-kubeconfig \
            --name kong-microservices-cluster \
            --region ap-southeast-1
      
      - name: Install ArgoCD CLI
        run: |
          curl -sSL -o argocd https://github.com/argoproj/argo-cd/releases/latest/download/argocd-linux-amd64
          chmod +x argocd
      
      - name: Sync ArgoCD Applications
        env:
          ARGOCD_SERVER: ${{ secrets.ARGOCD_SERVER }}
          ARGOCD_AUTH_TOKEN: ${{ secrets.ARGOCD_TOKEN }}
        run: |
          ./argocd app sync kong-api-gateway
          ./argocd app sync microservices
          ./argocd app wait kong-api-gateway --sync
          ./argocd app wait microservices --sync
      
      - name: Verify deployment
        run: kubectl rollout status deployment/product-service -n microservices
```

---

### Stage 5: Infrastructure Drift Detection

**Trigger:** Scheduled every 6 hours

```yaml
name: Detect Infrastructure Drift

on:
  schedule:
    - cron: '0 */6 * * *'  # Every 6 hours

env:
  AWS_REGION: ap-southeast-1

jobs:
  drift-detection:
    runs-on: ubuntu-latest
    
    permissions:
      id-token: write
      contents: read
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/GitHubActionsRole
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Detect CloudFormation Drift
        id: detect-drift
        run: |
          STACKS=$(aws cloudformation list-stacks \
            --region ${{ env.AWS_REGION }} \
            --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
            --query 'StackSummaries[?StackName==`*Kong*`].StackName' \
            --output text)
          
          DRIFT_DETECTED=false
          
          for STACK in $STACKS; do
            echo "Checking drift for stack: $STACK"
            
            DETECTION_ID=$(aws cloudformation detect-stack-drift \
              --stack-name $STACK \
              --region ${{ env.AWS_REGION }} \
              --query 'StackDriftDetectionId' \
              --output text)
            
            # Wait for detection to complete
            aws cloudformation wait stack-drift-detection-complete \
              --stack-drift-detection-id $DETECTION_ID \
              --region ${{ env.AWS_REGION }}
            
            DRIFT_STATUS=$(aws cloudformation describe-stack-drift-detection-status \
              --stack-drift-detection-id $DETECTION_ID \
              --region ${{ env.AWS_REGION }} \
              --query 'StackDriftDetectionStatus' \
              --output text)
            
            if [ "$DRIFT_STATUS" != "DETECTION_SUCCESSFUL" ]; then
              echo "⚠️  Drift detection failed for $STACK"
              continue
            fi
            
            # Check for actual drift
            DRIFT=$(aws cloudformation describe-stack-resource-drifts \
              --stack-name $STACK \
              --region ${{ env.AWS_REGION }} \
              --stack-resource-drift-status-filters MODIFIED \
              --query 'StackResourceDrifts')
            
            if [ ! -z "$DRIFT" ] && [ "$DRIFT" != "[]" ]; then
              echo "🔴 DRIFT DETECTED in $STACK"
              echo "$DRIFT" >> drift-report.json
              DRIFT_DETECTED=true
            else
              echo "✅ No drift in $STACK"
            fi
          done
          
          echo "DRIFT_DETECTED=$DRIFT_DETECTED" >> $GITHUB_OUTPUT
      
      - name: Detect Kubernetes Drift
        run: |
          # Compare Git state vs actual K8s state
          kubectl diff -f k8s/ -n microservices || true
          kubectl diff -f k8s/ -n kong || true
          
          # Check for manual changes
          kubectl get deployment -A -o yaml > actual-state.yaml
          
          # (In production, compare actual-state.yaml with Git versions)
      
      - name: Create drift report
        if: steps.detect-drift.outputs.DRIFT_DETECTED == 'true'
        run: |
          cat > drift-report.md << 'EOF'
          # Infrastructure Drift Report
          
          **Timestamp:** $(date -u)
          **Region:** ap-southeast-1
          
          ## Detected Drifts
          $(cat drift-report.json)
          
          **Action Required:** Review and reconcile infrastructure
          EOF
      
      - name: Create GitHub Issue for drift
        if: steps.detect-drift.outputs.DRIFT_DETECTED == 'true'
        uses: actions/github-script@v6
        with:
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('drift-report.md', 'utf8');
            
            github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title: '🔴 Infrastructure Drift Detected',
              body: report,
              labels: ['infrastructure', 'drift-detected', 'auto-generated']
            });
      
      - name: Auto-remediate non-critical drift
        if: steps.detect-drift.outputs.DRIFT_DETECTED == 'true'
        run: |
          # Re-apply IaC to fix drift (only for non-critical resources)
          cd infra
          cdk deploy --all --require-approval never --no-previous-parameters
      
      - name: Alert on critical drift
        if: steps.detect-drift.outputs.DRIFT_DETECTED == 'true'
        run: |
          # Send alert to ops team
          curl -X POST ${{ secrets.SLACK_WEBHOOK }} \
            -H 'Content-Type: application/json' \
            -d '{
              "text": "🔴 Critical Infrastructure Drift Detected",
              "blocks": [
                {
                  "type": "section",
                  "text": {
                    "type": "mrkdwn",
                    "text": "*Infrastructure Drift Alert*\n\nDrift has been detected in AWS CloudFormation stacks.\n\nCheck: <${{ github.server_url }}/${{ github.repository }}/actions/runs/${{ github.run_id }}|Drift Detection Workflow>"
                  }
                }
              ]
            }'
```

---

## Implementation Details

### Repository Structure for GitOps

```
kong-microservices-ref-app/
│
├── .github/workflows/
│   ├── ci.yml                      # PR validation (lint, test, scan)
│   ├── build-push-images.yml       # Build and push to ECR
│   ├── deploy-infra.yml            # Deploy IaC (AWS CDK)
│   ├── deploy-apps.yml             # Deploy apps via ArgoCD
│   ├── drift-detection.yml         # Detect infrastructure drift
│   └── rollback.yml                # Manual rollback workflow
│
├── services/
│   ├── product-service/
│   │   ├── src/
│   │   ├── pom.xml
│   │   └── Dockerfile
│   ├── order-service/
│   └── inventory-service/
│
├── infra/
│   ├── src/main/java/com/lab/infra/
│   │   ├── InfraApp.java
│   │   └── stacks/
│   ├── pom.xml
│   └── deploy.ps1
│
├── k8s/
│   ├── argocd/                     # ArgoCD ApplicationSet definitions
│   │   ├── kong-app.yaml
│   │   └── microservices-app.yaml
│   ├── kong/                       # Kong configuration
│   │   ├── kustomization.yaml
│   │   ├── kong-values.yaml
│   │   └── kong-routes.yaml
│   ├── services/                   # Microservices Kubernetes manifests
│   │   ├── product-service/
│   │   ├── order-service/
│   │   ├── inventory-service/
│   │   └── kustomization.yaml
│   ├── postgres/                   # Database configuration
│   │   ├── statefulset.yaml
│   │   └── pvc.yaml
│   └── monitoring/                 # Prometheus/Grafana
│       ├── prometheus-values.yaml
│       └── grafana-values.yaml
│
├── docs/
│   ├── DEPLOYMENT.md               # Deployment procedures
│   ├── ROLLBACK.md                 # Rollback procedures
│   └── MONITORING.md               # Monitoring setup
│
├── CONTRIBUTING.md
└── README.md
```

### GitHub Actions Secrets & Variables

```yaml
# Repository Secrets (Settings → Secrets and variables)
AWS_ACCOUNT_ID              # Your AWS account ID
AWS_ROLE_TO_ASSUME          # IAM role ARN for GitHub Actions
ARGOCD_SERVER               # ArgoCD server URL
ARGOCD_TOKEN                # ArgoCD API token
SLACK_WEBHOOK               # Slack webhook for notifications
SONARQUBE_TOKEN             # SonarQube token
CODECOV_TOKEN               # Codecov.io token
INFRACOST_API_KEY           # Infracost API key

# Repository Variables (Settings → Variables)
AWS_REGION                  # Default: ap-southeast-1
ECR_REGISTRY                # ECR registry URI
MINIMUM_TEST_COVERAGE       # Default: 70
MINIMUM_CODE_QUALITY        # Default: A (SonarQube)
```

### OIDC Setup for GitHub Actions

```bash
# Create IAM role with OIDC trust policy
# This allows GitHub Actions to assume role without storing credentials

aws iam create-role --role-name GitHubActionsRole \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [
      {
        "Effect": "Allow",
        "Principal": {
          "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com"
        },
        "Action": "sts:AssumeRoleWithWebIdentity",
        "Condition": {
          "StringEquals": {
            "token.actions.githubusercontent.com:aud": "sts.amazonaws.com"
          },
          "StringLike": {
            "token.actions.githubusercontent.com:sub": "repo:YOUR_ORG/kong-microservices-ref-app:ref:refs/heads/main"
          }
        }
      }
    ]
  }'

# Attach policies
aws iam attach-role-policy \
  --role-name GitHubActionsRole \
  --policy-arn arn:aws:iam::aws:policy/AmazonEKSFullAccess

aws iam attach-role-policy \
  --role-name GitHubActionsRole \
  --policy-arn arn:aws:iam::aws:policy/EC2ContainerRegistryPowerUser
```

---

## Infrastructure Drift Detection

### What is Infrastructure Drift?

Drift occurs when actual infrastructure state diverges from the desired state in Git:

```
Git (Desired State):
  ├─ EKS Cluster: Fargate only, no EC2 nodes
  ├─ Security Groups: Ingress on ports 80, 443
  ├─ Product Service: 2 replicas
  └─ Kong: Deployed via Helm

Actual Infrastructure:
  ├─ EKS Cluster: Fargate + 1 EC2 on-demand node (DRIFT!)
  ├─ Security Groups: Ingress on ports 22, 80, 443 (DRIFT!)
  ├─ Product Service: 1 replica (someone scaled down manually) (DRIFT!)
  └─ Kong: Helm chart has manual edits (DRIFT!)
```

### Common Drift Causes

| Cause | Severity | Prevention |
|-------|----------|-----------|
| Manual kubectl edits | HIGH | RBAC, read-only K8s API |
| AWS Console changes | CRITICAL | IAM policy restrictions |
| Manual scaling | MEDIUM | Pod Disruption Budgets |
| Image manual updates | HIGH | Registry image immutability |
| Configuration changes | MEDIUM | ConfigMap versioning |
| Security group rules | CRITICAL | AWS Config Rules |
| Tag deletions | LOW | CloudTrail audit |

### Multi-Layer Drift Detection

#### Layer 1: CloudFormation Drift (AWS Resources)

```bash
# Check AWS resources match IaC
aws cloudformation detect-stack-drift \
  --stack-name kong-microservices-networking-stack

# Results show:
# - DRIFTED (differs from template)
# - IN_SYNC (matches template)
# - UNKNOWN (detection failed)
```

#### Layer 2: Kubernetes Drift (Applications)

```bash
# Compare Git manifests vs actual cluster
kubectl diff -f k8s/

# Shows:
# - Added/removed resources
# - Changed specifications
# - Replica mismatches
```

#### Layer 3: Configuration Drift

```bash
# Check ConfigMaps and Secrets haven't been modified
kubectl get configmap -A -o yaml | md5sum
# Compare with Git version to detect manual edits
```

#### Layer 4: Policy Drift

```bash
# AWS Config Rules check policy compliance
aws configservice describe-compliance-by-config-rule \
  --compliance-types COMPLIANT NON_COMPLIANT
```

### Automated Remediation Strategy

```
Drift Detected
    ↓
Classify Drift
    ├─ CRITICAL (security, networking)
    │   ↓
    │   Alert on-call engineer
    │   Create incident ticket
    │   Pause auto-remediation
    │   Require manual review
    │
    ├─ MEDIUM (resources, replicas)
    │   ↓
    │   Create GitHub issue
    │   Attempt auto-remediation
    │   If successful, create PR
    │   If failed, alert engineer
    │
    └─ LOW (tags, labels)
        ↓
        Auto-remediate silently
        Log remediation action
        No alert needed
```

---

## Monitoring & Observability

### Pipeline Metrics Dashboard

**Key Metrics to Track:**

```yaml
Build Metrics:
  - Build success rate
  - Build time (trend)
  - Test coverage (trend)
  - Test pass rate
  - Code quality score

Deployment Metrics:
  - Deployment frequency
  - Lead time for changes
  - Mean time to recovery (MTTR)
  - Change failure rate
  - Rollback frequency

Infrastructure Metrics:
  - Cluster health
  - Pod uptime
  - Resource utilization
  - Drift incidents per week
  - Unplanned downtime
```

### Example Prometheus Queries

```promql
# Build success rate (last 7 days)
increase(github_actions_runs_total{conclusion="success"}[7d])
/
increase(github_actions_runs_total[7d])

# Average deployment time
histogram_quantile(0.95, rate(deployment_duration_seconds_bucket[5m]))

# Pod restart rate
rate(kube_pod_container_status_restarts_total[15m]) > 0

# Drift detection frequency
increase(drift_detection_runs_total[1d])
```

---

## Security & Best Practices

### 1. Secrets Management

```yaml
# ❌ DON'T: Commit secrets to Git
password: "mySecretPassword123"
api_key: "sk-1234567890abcdef"

# ✅ DO: Use GitHub Secrets
env:
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
  API_KEY: ${{ secrets.API_KEY }}

# ✅ BETTER: Use AWS Secrets Manager
- name: Fetch secrets
  run: |
    SECRET=$(aws secretsmanager get-secret-value \
      --secret-id prod/db-password \
      --query SecretString --output text)
    echo "DB_PASSWORD=$SECRET" >> $GITHUB_ENV
```

### 2. RBAC & Access Control

```yaml
# GitHub Actions permissions (least privilege)
permissions:
  contents: read        # Read code
  id-token: write       # OIDC token for AWS
  pull-requests: read   # Read PR information

# NOT: permissions: write-all  (overly permissive)
```

### 3. Code Review & Approvals

```yaml
# Branch protection rules
- Require pull request reviews before merging
- Require code review approval (minimum: 1)
- Require status checks to pass
  - Unit tests
  - Integration tests
  - Code quality checks
  - Security scans
- Restrict who can push to main (branch admins only)
- Automatically dismiss stale PR approvals
```

### 4. Image Security

```dockerfile
# ✅ DO: Use specific digest
FROM ubuntu:22.04@sha256:c7c2d...

# ❌ DON'T: Use floating tags
FROM ubuntu:latest
FROM ubuntu:22.04
```

### 5. Secret Scanning

```yaml
# .github/workflows/secret-scan.yml
- name: Secret scanning
  uses: gitleaks/gitleaks-action@v2
  with:
    config-path: .gitleaks.toml
    leak-exit-code: 1  # Fail if secrets found
```

---

## Practical Example: Full Deployment Flow

### Scenario: Deploy Product Service v2.0.0

```
1️⃣  Developer creates feature branch
    git checkout -b feature/product-v2

2️⃣  Makes code changes, commits, and pushes
    git add services/product-service/
    git commit -m "feat: add new product filtering"
    git push origin feature/product-v2

3️⃣  GitHub Actions PR validation triggers
    ✓ Code linting (Checkstyle)
    ✓ Unit tests (JUnit) - Coverage: 75%
    ✓ SAST scan (SonarQube)
    ✓ Docker build
    ✓ Image scan (Trivy)
    ✓ Integration tests

4️⃣  Developer creates pull request
    - Describes changes
    - References related issue

5️⃣  Code review by team lead
    - Checks code quality
    - Reviews test coverage
    - Approves PR

6️⃣  PR merged to main branch

7️⃣  GitHub Actions build pipeline triggers
    ✓ Builds Docker image
    ✓ Tags: latest, v2.0.0, sha-abc123
    ✓ Scans for vulnerabilities
    ✓ Pushes to ECR

8️⃣  Update k8s manifests
    - Update image URI with new digest
    - Commit manifest changes

9️⃣  ArgoCD detects manifest changes
    ✓ Syncs new manifests to cluster
    ✓ Rolling update: Old pod → New pod
    ✓ Health checks pass
    ✓ Deployment complete

🔟 Monitoring verifies deployment
    ✓ Smoke tests pass
    ✓ Service responding
    ✓ Error rates normal
    ✓ Latency acceptable

✅ Product Service v2.0.0 live in production
```

---

## Summary

### GitOps Workflow Benefits

✅ **Auditability** - Every change in Git with approval history
✅ **Automation** - From code commit to production automatically
✅ **Safety** - Pull requests enable code reviews
✅ **Consistency** - Same process for all deployments
✅ **Reliability** - Automated testing catches issues early
✅ **Disaster Recovery** - Infrastructure from Git state
✅ **Compliance** - Full audit trail for regulations

### Key Takeaways

1. **Git is the source of truth** - Everything (code, IaC, K8s) in Git
2. **Declare desired state** - Not imperative steps
3. **Automate everything** - Reduce manual error
4. **Monitor drift** - Detect and fix divergence
5. **Review all changes** - Pull requests for all deployments
6. **Test thoroughly** - Unit + Integration + E2E tests
7. **Secure secrets** - Use proper secret management

### Tools Checklist

- [x] Git repository (GitHub)
- [x] CI/CD platform (GitHub Actions)
- [x] Image registry (AWS ECR)
- [x] GitOps controller (ArgoCD)
- [x] Infrastructure as Code (AWS CDK)
- [x] Testing framework (JUnit, Maven Failsafe)
- [x] Code quality (SonarQube, Checkstyle)
- [x] Security scanning (Trivy, SonarQube)
- [x] Monitoring (Prometheus, Grafana)
- [x] Alerting (PagerDuty, Slack)

---

## References & Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [ArgoCD Documentation](https://argo-cd.readthedocs.io/)
- [AWS CDK Best Practices](https://docs.aws.amazon.com/cdk/latest/guide/)
- [Kubernetes Best Practices](https://kubernetes.io/docs/concepts/configuration/overview/)
- [Cloud Native Security Whitepaper](https://www.cncf.io/blog/2022/12/15/supply-chain-security/)
- [Infracost Documentation](https://www.infracost.io/docs/)
- [Checkov GitHub Rules](https://www.checkov.io/)

---

**Module Status:** Complete ✅

**Estimated Time to Complete:** 4-6 hours (with implementation)

**Next Module:** Session 3 - Advanced Kubernetes & Production-Ready Deployments
