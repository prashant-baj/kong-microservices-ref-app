# Module 1: Microservices Architecture Deep Dive


## Learning Objectives
- Distinguish between microservices and modular monoliths, identifying when decomposition is justified
- Explain the API Gateway pattern and how it centralizes cross-cutting concerns
- Describe Kong's architecture and its DB-less declarative configuration model
- Articulate the database-per-service pattern and its implications for data consistency
- Use Architecture Decision Records (ADRs) to document and communicate design rationale

## Format
This module is instructor-led with audience participation. No hands-on exercises -- the goal is to establish shared architectural context before building.

---

## Topic 1: Microservices vs. Modular Monolith

### When Decomposition Pays Off

The microservices conversation in most organizations starts backwards -- teams reach for decomposition as a default rather than a deliberate architectural trade-off. A modular monolith (well-defined module boundaries, separate compilation units, enforced dependency rules) gives you 80% of the organizational benefits of microservices with 20% of the operational complexity.

Decomposition into separately deployable services is justified when you have **independent release cadences** (the product catalog team ships twice a week, the order team ships biweekly), **independent scaling requirements** (product reads are 100x order writes), or **technology heterogeneity needs** (one team needs Python ML pipelines alongside Java services). If your entire system is built by one team shipping on one cadence, a modular monolith is almost always the better starting point.

The critical insight for architects: **microservices are an organizational scaling pattern first, and a technical scaling pattern second.** Conway's Law is not optional -- your architecture will mirror your communication structures whether you design for it or not.

### The Decomposition Decision Matrix

| Factor | Favors Monolith | Favors Microservices |
|--------|-----------------|---------------------|
| Team size | < 8 developers | Multiple teams (2-pizza rule) |
| Release cadence | Unified releases | Independent deployment needed |
| Scaling | Uniform load | Highly asymmetric load |
| Data coupling | Shared transactions needed | Bounded contexts are clear |
| Operational maturity | Limited DevOps capacity | Strong CI/CD, observability |
| Latency sensitivity | In-process calls needed | Network overhead acceptable |

### Facilitation Note
Ask participants: *"Raise your hand if you have worked on a system that was decomposed into microservices too early."* -- this reliably generates discussion and grounds the theory in real experience.

---

## Topic 2: The API Gateway Pattern

### Single Entry Point, Cross-Cutting Concerns

An API Gateway sits between clients and backend services, providing a single entry point that handles concerns no individual service should own:

```
Without Gateway:                    With Gateway:

Client knows about                  Client knows one endpoint
every service endpoint
                                    Client --> Gateway --> Service A
Client --> Service A (:8081)                          --> Service B
Client --> Service B (:8082)                          --> Service C
Client --> Service C (:8083)
                                    Gateway handles:
Client must handle:                 - Routing
- Service discovery                 - Rate limiting
- Auth token propagation            - Authentication
- Rate limiting (per-service?)      - CORS
- CORS (per-service?)               - Request/response transformation
- Retry logic                       - Load balancing
                                    - Circuit breaking
```

The gateway pattern solves a real coupling problem: without it, every client must understand the service topology. Adding, removing, or reorganizing services requires coordinated client changes. The gateway provides a stable API contract that decouples client evolution from service evolution.

### What Belongs in the Gateway vs. the Service

This is the most common source of gateway misuse. The rule of thumb:

- **Authentication** (who are you?) belongs in the **gateway** -- verify tokens once at the edge
- **Authorization** (what can you do?) belongs in the **service** -- only the service understands its domain rules
- **Rate limiting** belongs in the **gateway** -- protect the entire system at the entry point
- **Business validation** belongs in the **service** -- the gateway should not understand domain logic
- **Request transformation** belongs in the **gateway** only for client convenience (API versioning, field mapping)

> **Anti-pattern:** Putting business logic in gateway plugins creates a distributed monolith that is harder to reason about than either a real monolith or real microservices. The gateway should be a thin, stateless routing and policy layer.

---

## Topic 3: Kong Architecture

### Proxy Layer, Plugins, Declarative Config

Kong is a high-performance API gateway built on Nginx and OpenResty (LuaJIT). Understanding its layered architecture matters for configuration decisions:

```
Request Flow Through Kong:

Client Request
     |
     v
[Nginx/OpenResty]     <-- Connection handling, TLS termination
     |
     v
[Kong Plugin Chain]   <-- Ordered execution: auth, rate-limit, transform, log
     |
     v
[Upstream Proxy]      <-- Route to backend service, load balance
     |
     v
[Backend Service]
     |
     v
[Response Plugins]    <-- Response transformation, logging
     |
     v
Client Response
```

**Plugin execution order matters.** Kong executes plugins in a defined order based on plugin priority. Authentication plugins run before rate-limiting plugins, which run before logging plugins. This means rate limiting can apply per-consumer (after auth identifies the consumer) rather than just per-IP.

### DB-less Mode: Why We Use Declarative Config

Kong supports two configuration modes:

| Aspect | DB-backed | DB-less (Declarative) |
|--------|-----------|----------------------|
| Config storage | PostgreSQL/Cassandra | YAML file |
| Config changes | Admin API (imperative) | File reload (declarative) |
| GitOps friendly | Requires scripting | Native -- config is a file in version control |
| Multi-node sync | Database replication | File distribution (ConfigMap in K8s) |
| Operational overhead | Database to manage | No additional infrastructure |
| Dynamic changes | Hot reload via API | Requires container restart or SIGHUP |

For this training, we use DB-less mode because it aligns with infrastructure-as-code principles. The entire gateway configuration lives in a single `kong.yml` that is versioned, reviewed, and deployed alongside application code. In production, this file becomes a Kubernetes ConfigMap or is managed by decK (Kong's declarative CLI tool).

---

## Topic 4: Docker Compose as Local Microservices Platform

### Networking, DNS Resolution, Health Checks

Docker Compose provides a surprisingly capable local microservices platform. Understanding its networking model is essential because it mirrors how services discover each other in Kubernetes:

```yaml
# When you define services in docker-compose.yml:
services:
  product-service:
    # ...
  order-service:
    # ...

# Docker Compose automatically:
# 1. Creates a bridge network (session2_default)
# 2. Assigns each service a DNS name matching its service key
# 3. order-service can reach product-service at http://product-service:8081
```

**DNS resolution is the service discovery mechanism.** In Docker Compose, the service name IS the hostname. In Kubernetes, the service name IS the DNS entry. This means code written for Compose networking works unchanged in Kubernetes -- you externalize hostnames via environment variables and the platform provides resolution.

Health checks in Compose serve the same purpose as readiness probes in Kubernetes: they prevent traffic from reaching a service that is not ready to handle requests. Without health checks, `depends_on` only waits for the container to *start*, not for the application to be *ready*. A service that takes 15 seconds to initialize its database connection pool will receive traffic it cannot handle.

---

## Topic 5: Database-Per-Service

### The Isolation Boundary That Matters

Database-per-service is the most consequential pattern in microservices architecture. It means each service owns its data exclusively -- no shared tables, no cross-service joins, no distributed transactions.

```
WRONG (Shared Database):            RIGHT (Database-Per-Service):

+------------------+                +-----------+  +-----------+  +-----------+
| Shared Database  |                | product_db|  |inventory_db|  | order_db  |
|                  |                |           |  |           |  |           |
| products table   |                | products  |  | inventory |  | orders    |
| inventory table  |                +-----------+  +-----------+  +-----------+
| orders table     |                     ^              ^              ^
+------------------+                     |              |              |
  ^     ^     ^                    product-svc   inventory-svc   order-svc
  |     |     |
Svc A  Svc B  Svc C               Each service is the ONLY reader/writer
                                   of its database. Period.
Any service can read/write
any table. Schema changes          Schema changes are local decisions.
require cross-team coordination.   No coordination needed.
```

**Why this matters for architects:** The shared database is the number one reason microservices decompositions fail. When two services share a database, they are coupled at the data layer regardless of how cleanly you separate the code. Schema changes become distributed coordination problems. You have not actually decomposed -- you have a distributed monolith with network overhead.

In our lab, we use a single PostgreSQL instance with three separate databases (`product_db`, `inventory_db`, `order_db`). This is a pragmatic local development choice -- in production, these would typically be separate database instances or clusters. The logical separation enforces the same discipline.

---

## Discussion Prompt

> **"What is the most painful microservices failure you have experienced?"**

Facilitate 5-7 minutes of open discussion. Common themes to draw out:

- Distributed monolith (services coupled through shared database or synchronous call chains)
- Cascading failures from missing circuit breakers
- Data consistency nightmares from premature decomposition
- Operational overhead that exceeded the team's capacity
- "Microservices" that were really just separately deployed classes

Use these stories to motivate the patterns we will implement in the hands-on modules.

---

## Architecture Decision Record (ADR) Format

Throughout this session, we will make several architectural decisions. Architects should document these using ADRs. Here is the format we will reference:

```markdown
# ADR-NNN: [Title of Decision]

## Status
[Proposed | Accepted | Deprecated | Superseded by ADR-XXX]

## Context
What is the issue that we are seeing that is motivating this decision or change?
What forces are at play (technical, organizational, regulatory)?

## Decision
What is the change that we are proposing and/or doing?
State the decision in full sentences, with active voice.
"We will use Kong in DB-less mode as our API Gateway."

## Consequences

### Positive
- What becomes easier or possible as a result of this change?

### Negative
- What becomes harder as a result of this change?

### Risks
- What could go wrong? What are we accepting?

## Alternatives Considered
Brief description of other options and why they were rejected.
```

### Example ADR for This Session

```markdown
# ADR-001: Use API Gateway Pattern with Kong

## Status
Accepted

## Context
Our system has three independently developed services (product, inventory, order).
Clients currently need to know the address of each service. Cross-cutting concerns
(rate limiting, authentication, CORS) are implemented inconsistently across services.
We need a unified entry point that centralizes these concerns.

## Decision
We will deploy Kong API Gateway in DB-less (declarative) mode as the single entry
point for all client traffic. Kong will handle authentication, rate limiting, CORS,
and request routing. Individual services will not be exposed directly to clients.

## Consequences

### Positive
- Single entry point simplifies client integration
- Cross-cutting concerns managed in one place with consistent behavior
- Declarative config enables GitOps workflow (config reviewed in PRs)
- Services can evolve their internal APIs without breaking clients

### Negative
- Gateway becomes a single point of failure (mitigated by horizontal scaling in prod)
- Additional network hop adds ~1-2ms latency per request
- Team must learn Kong configuration and plugin model

### Risks
- Gateway accumulating business logic over time (mitigate with code review)
- Configuration drift between environments (mitigate with CI/CD pipeline)

## Alternatives Considered
- **Spring Cloud Gateway:** More familiar to Java teams, but ties gateway to JVM
  ecosystem and is harder to extend with non-Java plugins.
- **Nginx reverse proxy:** Simpler but lacks built-in plugin ecosystem for
  rate limiting, auth, and observability.
- **No gateway (direct client-to-service):** Rejected because it pushes
  cross-cutting concerns into each service and couples clients to topology.
```

---

## Key Takeaways for Module 1

1. **Microservices are a trade-off, not a goal.** Start with a modular monolith unless you have concrete scaling or organizational reasons to decompose.
2. **The API Gateway centralizes cross-cutting concerns** but must remain a thin routing/policy layer -- no business logic.
3. **Kong's DB-less mode** treats gateway configuration as code, enabling version control and review.
4. **Docker Compose networking** mirrors Kubernetes service discovery -- code written here transfers directly.
5. **Database-per-service is non-negotiable** for true decomposition. A shared database means you have a distributed monolith.

---

## Transition to Module 2

With the architectural context established, we will now containerize our three services and wire them together with Docker Compose. Open [Module 2: Containerization Exercise](module-2-containerization.md).
