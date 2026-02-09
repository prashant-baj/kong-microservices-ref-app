# Module 4: Inter-Service Communication

## Duration
25 minutes

## Learning Objectives
- Wire services together using Docker Compose DNS names
- Implement RestClient-based service clients (Spring Boot 3.2+)
- Apply Resilience4j circuit breaker to protect against cascading failures
- Experience failure scenarios and understand resilience patterns

## Prerequisites
- Module 3 complete (Kong routing verified)
- All three services running in Docker Compose

---

## Concepts

In our architecture, the order-service must call product-service and inventory-service synchronously during order creation. This is the simplest communication pattern but the most dangerous: if inventory-service is slow or down, order-service hangs or crashes, which cascades to the client. Circuit breakers prevent this cascade.

---

## Exercise 3: Wiring Services Together (15 min)

### Step 1: Service Discovery via Docker Compose DNS

In Docker Compose, each service is reachable by its service name. The order-service's `application-docker.yml`:

```yaml
services:
  product-service:
    url: http://product-service:8081
  inventory-service:
    url: http://inventory-service:8082
```

No Eureka, no Consul — Docker Compose's built-in DNS resolves `product-service` to the container's IP. Kong uses the same mechanism.

> **Design Decision:** For local development and small deployments, Docker DNS is sufficient. In production Kubernetes, you'd use Kubernetes Service DNS (`product-service.default.svc.cluster.local`). The pattern is the same — only the DNS provider changes. Service meshes (Istio) add a sidecar proxy layer on top.

### Step 2: RestClient Implementation

Spring Boot 3.2+ introduced `RestClient` as the modern synchronous HTTP client:

```java
@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(RestClient.Builder builder,
                                @Value("${services.product-service.url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ProductInfo getProduct(UUID productId) {
        return restClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .body(ProductInfo.class);
    }

    public record ProductInfo(UUID id, String name, BigDecimal price) {}
}
```

> **Design Decision:** RestClient over WebClient because our communication is synchronous. WebClient is reactive and adds complexity (Mono/Flux) without benefit when you're blocking anyway. RestClient over RestTemplate because RestTemplate is in maintenance mode — RestClient is the modern replacement.

### Step 3: Test the Full Order Flow

```bash
# 1. Create a product
curl -X POST http://localhost:8000/api/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Laptop","description":"Gaming laptop","price":1299.99,"category":"Electronics"}'
# Note the product ID from the response

# 2. Add stock for the product
curl -X POST http://localhost:8000/api/inventory/stock \
  -H "Content-Type: application/json" \
  -d '{"productId":"<PRODUCT_ID>","quantity":50}'

# 3. Place an order (requires API key)
curl -X POST http://localhost:8000/api/orders \
  -H "Content-Type: application/json" \
  -H "apikey: lab-api-key-2024" \
  -d '{"customerName":"Alice","items":[{"productId":"<PRODUCT_ID>","quantity":2}]}'

# 4. Verify stock was decremented
curl http://localhost:8000/api/inventory/stock/<PRODUCT_ID>
# quantityAvailable should be 48, quantityReserved should be 2
```

---

## Exercise 4: Failure Scenarios and Resilience (10 min)

### Step 1: Circuit Breaker Configuration

In `application.yml` for order-service:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventoryService:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
```

The circuit breaker has three states:
- **CLOSED** (normal): Requests flow through. Failures are counted.
- **OPEN** (tripped): Requests fail immediately without calling the backend. Protects the failing service.
- **HALF-OPEN** (testing): A few requests are allowed through to test recovery.

### Step 2: Simulate Service Failure

```bash
# Stop inventory-service
docker compose stop inventory-service

# Try to place an order — should fail fast (circuit breaker)
curl -X POST http://localhost:8000/api/orders \
  -H "Content-Type: application/json" \
  -H "apikey: lab-api-key-2024" \
  -d '{"customerName":"Bob","items":[{"productId":"<ID>","quantity":1}]}'
# Returns 422 (order creation failed)

# Restart inventory-service
docker compose start inventory-service

# Wait 10 seconds (wait-duration-in-open-state), then retry
# The circuit breaker enters HALF-OPEN, allows a test call through
```

### Step 3: Observe Kong Health Checks

```bash
# Check Kong's view of service health
curl http://localhost:8001/upstreams
```

> **Design Decision:** Circuit breakers protect the **caller** (order-service), not the **callee** (inventory-service). Without a circuit breaker, order-service threads would pile up waiting for timeouts from a dead inventory-service, eventually exhausting the thread pool and becoming unresponsive itself. The circuit breaker "fails fast" — returning an error in milliseconds instead of waiting for a 30-second timeout.

### Discussion: Resilience Patterns

| Pattern | Protects Against | Implementation |
|---------|-----------------|----------------|
| **Circuit Breaker** | Cascading failures | Resilience4j `@CircuitBreaker` |
| **Timeout** | Slow responses | RestClient timeout config |
| **Retry** | Transient failures | Resilience4j `@Retry` |
| **Bulkhead** | Thread pool exhaustion | Resilience4j `@Bulkhead` |
| **Rate Limiter** | Traffic spikes | Kong plugin (gateway level) |

> **Design Decision:** We use synchronous REST because it's simpler to reason about and debug. The trade-off is tight coupling — order-service can't complete without inventory-service being available. For higher availability, consider **event-driven architecture**: order-service publishes an `OrderCreated` event, inventory-service subscribes and reserves stock asynchronously. The order might be in a `PENDING` state until confirmation arrives.

---

## Verification
- Full order flow works end-to-end through Kong
- Stopping inventory-service triggers circuit breaker (fast failure)
- Restarting inventory-service allows recovery after wait duration

## Discussion Questions
1. When would you switch from synchronous REST to event-driven (Kafka/RabbitMQ)?
2. What's the right timeout value for inter-service calls? How do you decide?
3. How would you implement a saga pattern for a multi-step order flow?
4. What happens if the circuit breaker is too aggressive (opens too easily)?

## Common Pitfalls
- **Forgot the AOP starter:** Resilience4j annotations need `spring-boot-starter-aop`
- **Circuit breaker never opens:** Check that the sliding window has enough calls to evaluate
- **Service DNS not resolving:** Ensure services are on the same Docker network
