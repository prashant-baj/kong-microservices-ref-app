# Module 2: Containerization Exercise


## Learning Objectives
- Build multi-stage Dockerfiles that separate build-time and runtime concerns
- Configure a Docker Compose stack with PostgreSQL, multiple databases, and Spring Boot services
- Apply production-appropriate JVM settings for containerized workloads
- Implement health checks that enable reliable orchestration of dependent services

## Prerequisites
- Module 1 complete (architectural context established)
- Session 1 services (product-service, inventory-service, order-service) built and passing tests locally
- Docker Desktop running with at least 8 GB RAM allocated

## Concepts

Containerizing microservices is not about wrapping your JAR in a `FROM openjdk` and calling it done. The Dockerfile is an architectural artifact -- it encodes decisions about build reproducibility, image size, security surface area, and runtime behavior. Multi-stage builds separate the build environment (Maven, source code, test dependencies) from the runtime environment (JRE, application JAR), producing images that are smaller, faster to pull, and have fewer CVE-prone packages.

For JVM applications, containers introduce a subtlety that trips up experienced developers: the JVM must be told about container memory limits, or it will try to use the host's total memory and get OOM-killed. Modern JVMs (17+) are container-aware by default, but explicit configuration with `-XX:MaxRAMPercentage` makes the intent clear and avoids surprises when base images change.

---

## Exercise 1: Dockerizing the Services

### Step 1: Understand Multi-Stage Dockerfiles

Each service gets the same Dockerfile structure. The key architectural decision is separating build from runtime -- the build stage contains Maven, source code, and all build-time dependencies (hundreds of megabytes). The runtime stage contains only the JRE and the built JAR.

Create `product-service/Dockerfile`:

```dockerfile
# ============================================================
# Stage 1: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Maven wrapper and POM first (layer caching optimization)
# These layers are invalidated only when dependencies change,
# not when source code changes. This saves minutes on rebuilds.
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies in a separate layer
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Now copy source code (this layer changes frequently)
COPY src src

# Build the application, skip tests (they ran in CI already)
RUN ./mvnw package -DskipTests -B

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the built JAR from the build stage
COPY --from=build /workspace/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app
USER appuser

# JVM container-aware settings
# MaxRAMPercentage=75: use 75% of container memory limit for heap
# This leaves 25% for metaspace, thread stacks, NIO buffers, and OS overhead
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
```

> **Design Decision: Multi-stage builds -- why not just copy the JAR?**
>
> You *could* build locally and `COPY target/*.jar app.jar` in a single-stage Dockerfile. This is faster for local development but breaks build reproducibility. The JAR built on a developer's macOS laptop with JDK 21.0.1 may differ from one built on a CI server with JDK 21.0.3. Multi-stage builds guarantee that the same Dockerfile produces the same artifact regardless of where `docker build` runs. The build environment is the Dockerfile, not the host machine.

> **Design Decision: JVM in containers -- `-XX:MaxRAMPercentage` vs. fixed `-Xmx`**
>
> Using `-Xmx512m` hardcodes the heap size, creating a mismatch if you change the container memory limit in Compose or Kubernetes. `-XX:MaxRAMPercentage=75.0` dynamically calculates the heap based on the container's cgroup memory limit. When you scale from 512 MB to 1 GB container memory, the JVM automatically adjusts. The 75% ratio leaves room for metaspace (~50-100 MB), thread stacks (1 MB per thread), NIO direct buffers, and the OS. In high-throughput services with many threads, you might lower this to 65%.

Apply the same Dockerfile pattern to `inventory-service/Dockerfile` (change `EXPOSE` to `8082`) and `order-service/Dockerfile` (change `EXPOSE` to `8083`).

---

### Step 2: Create docker-compose.yml

This Compose file defines the complete local microservices platform. Create `docker-compose.yml` in the project root:

```yaml
version: "3.9"

services:
  # ============================================================
  # Database: Single PostgreSQL instance, three logical databases
  # ============================================================
  postgres:
    image: postgres:16-alpine
    container_name: microservices-postgres
    environment:
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: admin123
      POSTGRES_DB: postgres  # default DB, init script creates the rest
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infrastructure/postgres/init-databases.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U admin -d product_db && pg_isready -U admin -d inventory_db && pg_isready -U admin -d order_db"]
      interval: 5s
      timeout: 3s
      retries: 5
      start_period: 10s

  # ============================================================
  # Product Service
  # ============================================================
  product-service:
    build:
      context: ./product-service
      dockerfile: Dockerfile
    container_name: product-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/product_db
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      SERVER_PORT: 8081
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 512M

  # ============================================================
  # Inventory Service
  # ============================================================
  inventory-service:
    build:
      context: ./inventory-service
      dockerfile: Dockerfile
    container_name: inventory-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/inventory_db
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      SERVER_PORT: 8082
    ports:
      - "8082:8082"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8082/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 512M

  # ============================================================
  # Order Service
  # ============================================================
  order-service:
    build:
      context: ./order-service
      dockerfile: Dockerfile
    container_name: order-service
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/order_db
      SPRING_DATASOURCE_USERNAME: admin
      SPRING_DATASOURCE_PASSWORD: admin123
      SERVER_PORT: 8083
      # Inter-service communication URLs (Docker DNS resolution)
      PRODUCT_SERVICE_URL: http://product-service:8081
      INVENTORY_SERVICE_URL: http://inventory-service:8082
    ports:
      - "8083:8083"
    depends_on:
      product-service:
        condition: service_healthy
      inventory-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8083/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 512M

volumes:
  postgres-data:
```

Note the key architectural choices embedded in this file:

1. **Service names ARE hostnames** -- `product-service` in the Compose file becomes the DNS name on the Docker network. The order-service reaches product-service at `http://product-service:8081`. No service registry needed.
2. **`depends_on` with `condition: service_healthy`** -- this is not just startup ordering. It waits for the health check to pass, meaning the database is actually accepting connections before services start.
3. **Memory limits via `deploy.resources.limits.memory`** -- this sets the cgroup memory limit that `-XX:MaxRAMPercentage` reads. Without this, the JVM sees the host's total memory.

---

### Step 3: Configure Per-Service Databases via Init Script

Create `infrastructure/postgres/init-databases.sql`:

```sql
-- This script runs once when the PostgreSQL container is first created.
-- It creates the three databases that enforce our database-per-service boundary.

-- Product Service Database
CREATE DATABASE product_db;
GRANT ALL PRIVILEGES ON DATABASE product_db TO admin;

-- Inventory Service Database
CREATE DATABASE inventory_db;
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO admin;

-- Order Service Database
CREATE DATABASE order_db;
GRANT ALL PRIVILEGES ON DATABASE order_db TO admin;

-- Note: In production, each database would have its own dedicated user
-- with least-privilege access. Using a shared 'admin' user is a
-- development convenience only.
```

> **Design Decision: Single Postgres with multiple databases vs. separate instances**
>
> In production, you would run separate database instances (or managed database services) for true isolation -- separate failure domains, independent scaling, independent backup/restore. Locally, a single Postgres instance with logical databases provides the same *application-level* isolation (services cannot accidentally join across databases) with a fraction of the resource cost. The services do not know or care whether they are talking to one Postgres instance or three -- the connection string is their only coupling point.

Create the Spring `docker` profile for each service. For example, `product-service/src/main/resources/application-docker.yml`:

```yaml
# Docker profile: activated via SPRING_PROFILES_ACTIVE=docker
# All values are overridden by environment variables in docker-compose.yml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

logging:
  level:
    root: INFO
    com.example: DEBUG
  pattern:
    console: "%d{ISO8601} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n"
```

---

### Step 4: Add Health Checks

Health checks are not optional in orchestrated environments. Without them, the orchestrator (Compose, Kubernetes) cannot distinguish between "container is running" and "application is ready to serve traffic."

Spring Boot Actuator provides the `/actuator/health` endpoint. Ensure each service has the Actuator dependency:

```xml
<!-- In each service's pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

The health check in Compose uses `curl` inside the container:

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
  interval: 10s    # Check every 10 seconds
  timeout: 5s      # Fail if no response in 5 seconds
  retries: 5       # Mark unhealthy after 5 consecutive failures
  start_period: 30s  # Grace period for JVM startup (don't count failures)
```

> **Design Decision: Health check patterns -- HTTP vs. TCP vs. command**
>
> - **HTTP health check** (`curl` to `/actuator/health`): Verifies the application layer is responding AND that its dependencies (database connections) are healthy. This is the most informative check.
> - **TCP health check** (`pg_isready`, port check): Verifies the process is listening. Useful for databases where an HTTP endpoint is not available.
> - **Command health check** (arbitrary shell command): Most flexible but requires the tool to be installed in the container image. We use `curl`, which is available in Alpine-based images but may need to be added to minimal/distroless images.
>
> **Architect's rule of thumb:** Use the most specific health check available. An HTTP 200 from `/actuator/health` tells you the app is ready. A TCP connection to port 8081 only tells you Tomcat started -- the database pool might still be initializing.

---

### Step 5: Build and Run the Full Stack

```bash
# Build all images (first run takes 3-5 minutes for dependency download)
docker compose build

# Start the entire stack in detached mode
docker compose up -d

# Watch the startup sequence -- note the dependency ordering
docker compose logs -f

# In a separate terminal, monitor health status
docker compose ps
```

Expected output from `docker compose ps` when everything is healthy:

```
NAME                    STATUS                    PORTS
microservices-postgres  Up 30 seconds (healthy)   0.0.0.0:5432->5432/tcp
product-service         Up 25 seconds (healthy)   0.0.0.0:8081->8081/tcp
inventory-service       Up 25 seconds (healthy)   0.0.0.0:8082->8082/tcp
order-service           Up 20 seconds (healthy)   0.0.0.0:8083->8083/tcp
```

Verify each service individually:

```bash
# Product service health
curl http://localhost:8081/actuator/health
# Expected: {"status":"UP","components":{"db":{"status":"UP",...},...}}

# Inventory service health
curl http://localhost:8082/actuator/health

# Order service health
curl http://localhost:8083/actuator/health
```

---

## Verification

Your exercise is complete when:

- [ ] `docker compose ps` shows all 4 containers with `(healthy)` status
- [ ] Each service responds to `/actuator/health` with `{"status":"UP"}` and database component healthy
- [ ] `docker images` shows your service images are under 300 MB each (multi-stage build benefit)
- [ ] Stopping and restarting (`docker compose down && docker compose up -d`) recovers cleanly

Quick size check:

```bash
docker images | grep -E "product-service|inventory-service|order-service"
# Each image should be ~250-300 MB (JRE Alpine + app JAR)
# Compare: a single-stage JDK build would be ~500-700 MB
```

---

## Discussion Questions

1. **Layer caching:** We copy `pom.xml` before `src/`. Why does this ordering matter for Docker layer caching? What happens to build times if you reverse the order?

2. **Base image selection:** We use `eclipse-temurin:21-jre-alpine`. What are the trade-offs vs. `eclipse-temurin:21-jre-jammy` (Ubuntu-based)? When would you choose one over the other?

3. **Memory limits:** We set 512 MB per service. How would you determine the right memory limit for a production service? What metrics would you monitor?

4. **Init containers vs. init scripts:** In Kubernetes, you might use an init container instead of mounting an init SQL script. When is an init container the better pattern?

---

## Common Pitfalls

| Pitfall | Symptom | Fix |
|---------|---------|-----|
| Missing `chmod +x mvnw` | `permission denied` during build | Add `RUN chmod +x mvnw` before running Maven |
| No `start_period` on health check | Service marked unhealthy during JVM startup | Add `start_period: 30s` to account for Spring Boot initialization |
| Host port conflict | `Bind for 0.0.0.0:5432 failed: port already allocated` | Stop local PostgreSQL or change the host port mapping |
| `depends_on` without `condition` | Service starts before database is ready | Use `condition: service_healthy` (requires health check on dependency) |
| Fixed `-Xmx` ignoring container limit | JVM OOM-killed, container shows `137` exit code | Use `-XX:MaxRAMPercentage` and set container memory limit |
| CRLF line endings in shell scripts | `/bin/sh: mvnw: not found` or `\r` errors | Configure Git with `core.autocrlf=false` or add `.gitattributes` |

---

## Transition to Module 3

With all services containerized and healthy, we will now add Kong API Gateway as the single entry point. Open [Module 3: API Gateway with Kong](module-3-api-gateway-kong.md).
