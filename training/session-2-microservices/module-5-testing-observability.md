# Module 5: E2E Testing and Observability


## Learning Objectives
- Write automated E2E tests against the full Docker Compose stack through Kong
- Understand correlation IDs for distributed request tracing
- Evaluate the testing pyramid for microservices architectures

## Prerequisites
- Module 4 complete (full stack running, inter-service communication verified)

---

## Exercise 5: Integration Testing the Architecture

### Step 1: REST Assured Tests Through the Gateway

The `integration-tests` module uses REST Assured to test the full stack through Kong:

```java
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullOrderFlowIT {

    @BeforeAll
    static void setUp() {
        RestAssured.baseURI = "http://localhost:8000"; // Kong proxy
    }

    @Test @Order(1)
    void should_CreateProduct_Through_Gateway() {
        productId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("name", "Test Laptop", "price", 999.99,
                         "description", "For testing", "category", "Electronics"))
        .when()
            .post("/api/products")
        .then()
            .statusCode(201)
            .header("Location", notNullValue())
            .extract().jsonPath().getString("id");
    }

    @Test @Order(2)
    void should_AddStock_Through_Gateway() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("productId", productId, "quantity", 50))
        .when()
            .post("/api/inventory/stock")
        .then()
            .statusCode(201);
    }

    @Test @Order(3)
    void should_CreateOrder_Through_Gateway_WithApiKey() {
        given()
            .contentType(ContentType.JSON)
            .header("apikey", "lab-api-key-2024")
            .body(Map.of("customerName", "Tester",
                "items", List.of(Map.of("productId", productId, "quantity", 2))))
        .when()
            .post("/api/orders")
        .then()
            .statusCode(201)
            .body("status", equalTo("CONFIRMED"));
    }
}
```

Run with: `mvn verify -pl integration-tests -Dgateway.url=http://localhost:8000`

> **Design Decision:** E2E tests run against the real stack, not mocks. They catch integration issues (serialization, routing, auth) that unit tests miss. The trade-off: they're slow (~10s per test), brittle (depend on service startup order), and hard to debug. Use sparingly — test the critical paths, not every edge case.

### Step 2: Correlation IDs for Distributed Tracing

In a microservices system, a single user request flows through multiple services. Without a correlation ID, connecting logs across services is impossible.

Add a filter to each service that reads or generates a correlation ID:

```java
@Component
public class CorrelationIdFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        String correlationId = request.getHeader("X-Correlation-ID");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

Then in `application.yml`, include it in log format:
```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] [%X{correlationId}] %-5level %logger{36} - %msg%n"
```

When order-service calls inventory-service, it forwards the correlation ID:
```java
restClient.post()
    .uri("/api/inventory/reservations")
    .header("X-Correlation-ID", MDC.get("correlationId"))
    .body(request)
    .retrieve()
    .body(ReservationResponse.class);
```

### Step 3: View Distributed Logs

```bash
# View logs across all services with correlation IDs
docker compose logs -f | grep "correlationId"
```

> **Design Decision:** Correlation IDs are the minimum viable observability for microservices. In production, you'd use OpenTelemetry with distributed tracing (Jaeger, Zipkin) to get visual request flow diagrams. The three pillars of observability are: **Logs** (what happened), **Metrics** (how much/how fast), **Traces** (where time was spent).

---

## Verification
- `mvn verify -pl integration-tests` passes (requires Docker Compose stack running)
- Logs from all three services show the same correlation ID for a single order request

## Discussion Questions
1. How many E2E tests are "enough"? What's your heuristic?
2. When would you invest in contract tests (Pact) vs. more E2E tests?
3. What observability tooling would you add first in production: metrics, traces, or better logs?

## Common Pitfalls
- **Tests fail because services aren't started:** E2E tests require `docker compose up` first
- **Port conflicts:** Ensure nothing else is running on 8000, 8081-8083, 5432
- **Test order dependency:** Use `@TestMethodOrder` if tests depend on each other
