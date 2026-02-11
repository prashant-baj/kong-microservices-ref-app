# Module 1: TDD at the Architecture Level


## Learning Objectives
- Reframe TDD from "testing technique" to "design tool" for architects
- Map the test pyramid to microservices-specific testing concerns
- Understand the Walking Skeleton approach for new services

---

## The Test Pyramid in Microservices Context

The classic test pyramid (unit → integration → E2E) takes on new dimensions in microservices:

```
         ╱ E2E through Gateway ╲        ← Slowest, most brittle, highest confidence
        ╱  Contract Tests       ╲       ← Verify inter-service agreements
       ╱   Integration Tests     ╲      ← Service + real DB (Testcontainers)
      ╱    Component Tests        ╲     ← @WebMvcTest, @DataJpaTest
     ╱     Unit Tests              ╲    ← Fastest, most numerous, lowest confidence
```

**Key insight for architects:** The middle layers (contract and integration) are where most microservices teams under-invest, yet they catch the failures that matter most — broken inter-service contracts and data persistence bugs.

> **Discussion Prompt:** In your current projects, which layer of the test pyramid is weakest? What failures have slipped through because of that gap?

---

## TDD as API Design

When you write a test before the implementation, your test becomes the **first consumer** of your API. This forces decisions:

- What should the method signature look like?
- What does the caller need to provide?
- What should the response contain?
- What happens when things go wrong?

These are **architectural decisions**, not testing decisions. The test is merely the mechanism that forces you to make them explicit.

**Example:** Writing `productService.createProduct(request)` in a test before the method exists forces you to decide: Does it take a domain object or a DTO? Does it return the entity or a response object? Does it throw on duplicate names or return an error result?

---

## The Walking Skeleton Approach

For new microservices, start with a failing end-to-end test that exercises the thinnest possible slice through all layers:

1. **Red:** Write a test that POSTs to `/api/products` and expects 201
2. **Green:** Implement just enough: Controller → Service → Repository → Database
3. **Refactor:** Now you have a working skeleton to build on

This approach validates your infrastructure (Spring wiring, database connectivity, serialization) before you invest in business logic.

---

## When NOT to TDD

TDD is not universally applicable. For architects, knowing when to skip it is as important as knowing when to apply it:

| Skip TDD | Apply TDD |
|-----------|-----------|
| Configuration classes (`@Configuration`) | Business logic and rules |
| DTO definitions (records) | Service layer orchestration |
| Framework boilerplate | API contracts (controller tests) |
| Database migrations (SQL) | Error handling and edge cases |
| Docker/infrastructure config | Inter-service client behavior |

---

## TDD Myths for Senior Engineers

**"TDD slows me down"** — It slows down initial coding but prevents the debug-fix-redeploy cycles that consume far more time. For microservices where each deploy cycle involves containers, the payoff is even larger.

**"I can write tests after"** — Tests written after implementation test what you built, not what you should have built. They miss edge cases because you unconsciously avoid them.

**"Mocks make tests worthless"** — Mocks test the *contract* between components. If your mock doesn't match reality, that's a signal you need contract tests, not that mocking is broken.

**"100% coverage is the goal"** — Coverage measures what code runs during tests, not what behavior is verified. Aim for meaningful assertions on critical paths, not line coverage.

> **Discussion Prompt:** Which of these myths resonates most with your team's current attitude? How would you address it?

---

## Testing Strategy Trade-offs

| Approach | Speed | Confidence | Maintenance Cost |
|----------|-------|------------|------------------|
| Unit + Mocks | Fast | Low-Medium | Low |
| Integration + Testcontainers | Slow | High | Medium |
| Contract Tests (Pact/Spring Cloud Contract) | Medium | Medium-High | Medium |
| E2E through Gateway | Very Slow | Very High | High |

**Architect's decision:** Choose the combination that gives you adequate confidence for the risk level of each service. A payment service needs more integration tests than a product catalog.

---

## Key Takeaways
1. TDD is a design technique that happens to produce tests
2. Your test is the first consumer of your API — design it accordingly
3. The Walking Skeleton validates infrastructure before business logic
4. Know when TDD adds value and when it's ceremony
5. In microservices, invest in the middle of the pyramid (integration + contract)
