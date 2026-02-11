# Module 3: API Gateway with Kong


## Learning Objectives
- Configure Kong declaratively in DB-less mode (GitOps-aligned)
- Map service routes and understand path-based routing design
- Apply cross-cutting concerns as Kong plugins (rate limiting, auth, CORS, logging)
- Distinguish what belongs in the gateway vs. in the service

## Prerequisites
- Module 2 complete (services containerized and running in Docker Compose)

---

## Concepts

The API Gateway is a single entry point that routes client requests to backend microservices. Kong in DB-less (declarative) mode keeps the entire gateway configuration in a single YAML file under version control. This aligns with GitOps: the gateway config is code, reviewed in PRs, and deployed alongside your services.

---

## Exercise 2a: Kong Declarative Configuration (15 min)

### Step 1: Understand the kong.yml Structure

Kong's declarative config has four key sections:

```yaml
_format_version: "3.0"

services:        # Backend microservices Kong proxies to
  - name: product-service
    url: http://product-service:8081    # Docker Compose DNS name
    routes:
      - name: product-routes
        paths:
          - /api/products               # Client-facing path
        strip_path: false               # Forward the full path to the service

plugins:         # Cross-cutting concerns applied globally or per-service
consumers:       # API consumers (for authentication)
```

> **Design Decision:** `strip_path: false` means Kong forwards `/api/products/123` as-is to the backend. If `strip_path: true`, Kong would strip `/api/products` and forward just `/123`. We keep it false because our services already expect the full path.

### Step 2: Create the Declarative Config

Create `api-gateway/kong.yml` with routes for all three services:

```yaml
_format_version: "3.0"

services:
  - name: product-service
    url: http://product-service:8081
    routes:
      - name: product-routes
        paths: [/api/products]
        strip_path: false
        protocols: [http]

  - name: inventory-service
    url: http://inventory-service:8082
    routes:
      - name: inventory-routes
        paths: [/api/inventory]
        strip_path: false
        protocols: [http]

  - name: order-service
    url: http://order-service:8083
    routes:
      - name: order-routes
        paths: [/api/orders]
        strip_path: false
        protocols: [http]
```

> **Design Decision:** Route design IS API design. We use path-based routing (`/api/products`, `/api/orders`). Alternatives include header-based routing (version in `Accept` header) or subdomain-based (`products.api.example.com`). Path-based is simplest and most widely understood.

### Step 3: Add Kong to Docker Compose

```yaml
kong:
  image: kong:3.6
  environment:
    KONG_DATABASE: "off"                    # DB-less mode
    KONG_DECLARATIVE_CONFIG: /etc/kong/kong.yml
    KONG_PROXY_LISTEN: 0.0.0.0:8000
    KONG_ADMIN_LISTEN: 0.0.0.0:8001
  ports:
    - "8000:8000"      # Proxy port (client traffic)
    - "8001:8001"      # Admin API (for inspection)
  volumes:
    - ./api-gateway/kong.yml:/etc/kong/kong.yml:ro
```

### Step 4: Test Routing

```bash
# Through Kong gateway (port 8000)
curl http://localhost:8000/api/products
curl http://localhost:8000/api/inventory/stock/{productId}
curl http://localhost:8000/api/orders    # Will fail — needs API key (added next)

# Verify Kong headers
curl -I http://localhost:8000/api/products
# Look for: Via: kong/3.6
```

---

## Exercise 2b: Kong Plugins for Cross-Cutting Concerns (15 min)

### Step 1: Global Rate Limiting

```yaml
plugins:
  - name: rate-limiting
    config:
      minute: 100
      policy: local
```

This limits all routes to 100 requests/minute. Test:
```bash
# Send 101 requests rapidly
for i in $(seq 1 101); do curl -s -o /dev/null -w "%{http_code}" http://localhost:8000/api/products; done
# The 101st should return 429 (Too Many Requests)
```

> **Design Decision:** `policy: local` stores rate limit counters in Kong's memory — fine for a single instance. In production with multiple Kong instances, use `policy: redis` so counters are shared. The rate limit applies **at the gateway**, protecting backend services from traffic spikes.

### Step 2: CORS Plugin

```yaml
  - name: cors
    config:
      origins: ["*"]
      methods: [GET, POST, PUT, DELETE, OPTIONS]
      headers: [Content-Type, Authorization]
      max_age: 3600
```

### Step 3: Request Logging

```yaml
  - name: file-log
    config:
      path: /dev/stdout
```

Logs all requests to Kong's stdout (visible via `docker compose logs kong`).

### Step 4: Key Authentication on Order Routes

Orders involve financial transactions — require an API key:

```yaml
  - name: key-auth
    service: order-service      # Only applies to order routes
    config:
      key_names: [apikey]
      hide_credentials: true    # Strip the key before forwarding to backend

consumers:
  - username: lab-user
    keyauth_credentials:
      - key: lab-api-key-2024
```

Test:
```bash
# Without API key — 401
curl http://localhost:8000/api/orders

# With API key — 200
curl -H "apikey: lab-api-key-2024" http://localhost:8000/api/orders

# Products don't require a key — 200
curl http://localhost:8000/api/products
```

> **Design Decision:** Authentication at the gateway, authorization in the service. Kong verifies "is this caller allowed to talk to the order service?" The order-service itself checks "is this caller allowed to perform THIS operation?" (e.g., can they cancel someone else's order?). This separation is a core API gateway pattern.

### Step 5: Reload Kong After Config Changes

```bash
docker compose restart kong
```

---

## Verification

```bash
# All routes work through gateway
curl http://localhost:8000/api/products              # 200
curl http://localhost:8000/api/inventory/stock/...    # 200 or 404
curl -H "apikey: lab-api-key-2024" http://localhost:8000/api/orders  # 200
curl http://localhost:8000/api/orders                 # 401 (no key)
curl http://localhost:8000/api/unknown                # 404 (no route)
```

## Discussion Questions
1. What's the difference between Kong in DB-less mode vs. DB-backed mode? When would you choose each?
2. Should rate limiting be per-user, per-IP, or global? What are the trade-offs?
3. When would you choose Spring Cloud Gateway over Kong? What about Envoy or Istio?
4. What's the risk of putting too much logic in the gateway?

## Common Pitfalls
- **Kong config syntax error:** Use `docker compose logs kong` to see parsing errors
- **Route conflict:** Two routes matching the same path — Kong uses longest prefix match
- **Forgot to restart Kong:** Config changes require `docker compose restart kong`
- **strip_path confusion:** If your backend returns 404, check if strip_path is eating your path prefix
