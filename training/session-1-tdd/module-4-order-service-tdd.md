# Module 4: TDD Exercise — Order Service

## Duration
20 minutes

## Learning Objectives
- Test-drive a service that orchestrates calls to other microservices
- Implement compensation logic (rollback on partial failure) via TDD
- Understand the trade-offs of mocking external service clients

## Prerequisites
- Modules 2 and 3 complete
- Understanding of product-service and inventory-service contracts

---

## Concepts

The order-service is architecturally the most interesting: it **orchestrates** across product-service (price lookup) and inventory-service (stock reservation). This introduces the challenge of testing code that depends on external services. We mock the service clients at the unit level — and discuss where contract tests would close the gap.

---

## Exercise 4: Service-to-Service Calls and Contract Testing (20 min)

### Step 1: RED — Create order with mocked service clients

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductServiceClient productServiceClient;
    @Mock private InventoryServiceClient inventoryServiceClient;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productServiceClient,
                                        inventoryServiceClient);
    }

    @Test
    void should_CreateOrder_When_AllProductsExistAndStockAvailable() {
        UUID productId = UUID.randomUUID();
        var request = new CreateOrderRequest("Alice", List.of(
            new OrderLineItemRequest(productId, 2)
        ));

        when(productServiceClient.getProduct(productId))
            .thenReturn(new ProductInfo(productId, "Laptop", new BigDecimal("999.99")));
        when(orderRepository.save(any(Order.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(inventoryServiceClient.reserveStock(eq(productId), any(), eq(2)))
            .thenReturn(new ReservationInfo(UUID.randomUUID(), productId, 2));

        Order result = orderService.createOrder(request);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getCustomerName()).isEqualTo("Alice");
        assertThat(result.getLineItems()).hasSize(1);
        assertThat(result.getTotalAmount())
            .isEqualByComparingTo(new BigDecimal("1999.98"));
    }
}
```

### Step 2: GREEN — Implement `OrderService.createOrder()`

The flow:
1. Create `Order` with status `CREATED`
2. For each item: call `productServiceClient.getProduct()` to get name and price
3. Create `LineItem` with product details, add to order
4. Calculate `totalAmount`
5. Save order
6. Reserve inventory for each line item
7. Set status to `CONFIRMED`, save

### Step 3: RED — Compensation on failure

This is the critical architectural test: what happens when one reservation succeeds but the next fails?

```java
@Test
void should_FailOrderAndCompensate_When_InventoryReservationFails() {
    UUID product1 = UUID.randomUUID();
    UUID product2 = UUID.randomUUID();
    UUID reservation1Id = UUID.randomUUID();

    var request = new CreateOrderRequest("Bob", List.of(
        new OrderLineItemRequest(product1, 1),
        new OrderLineItemRequest(product2, 1)
    ));

    when(productServiceClient.getProduct(product1))
        .thenReturn(new ProductInfo(product1, "Laptop", new BigDecimal("999.99")));
    when(productServiceClient.getProduct(product2))
        .thenReturn(new ProductInfo(product2, "Monitor", new BigDecimal("499.99")));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    // First reservation succeeds
    when(inventoryServiceClient.reserveStock(eq(product1), any(), eq(1)))
        .thenReturn(new ReservationInfo(reservation1Id, product1, 1));
    // Second reservation fails
    when(inventoryServiceClient.reserveStock(eq(product2), any(), eq(1)))
        .thenThrow(new RuntimeException("Insufficient stock"));

    assertThatThrownBy(() -> orderService.createOrder(request))
        .isInstanceOf(OrderCreationException.class);

    // Verify compensation: first reservation was cancelled
    verify(inventoryServiceClient).cancelReservation(reservation1Id);
}
```

### Step 4: GREEN — Implement compensation logic

```java
// In OrderService.createOrder():
List<UUID> successfulReservations = new ArrayList<>();
try {
    for (LineItem item : order.getLineItems()) {
        ReservationInfo reservation = inventoryServiceClient.reserveStock(
            item.getProductId(), order.getId(), item.getQuantity());
        successfulReservations.add(reservation.id());
    }
    order.setStatus(OrderStatus.CONFIRMED);
} catch (Exception e) {
    // Compensate: cancel all successful reservations
    successfulReservations.forEach(inventoryServiceClient::cancelReservation);
    order.setStatus(OrderStatus.FAILED);
    orderRepository.save(order);
    throw new OrderCreationException("Failed to reserve inventory", e);
}
```

> **Design Decision:** We chose **compensation** over distributed transactions (2PC) because: (1) 2PC requires all services to support it — Kong, PostgreSQL instances across services would need XA, (2) 2PC holds locks across services, killing throughput, (3) compensation is the standard microservices approach. The trade-off: compensation can fail too (what if `cancelReservation` throws?). In production, you'd add a retry queue or saga orchestrator.

### Step 5: RED/GREEN — Controller test for POST /api/orders

```java
@Test
void should_Return201WithLocation_When_OrderCreated() throws Exception {
    Order order = new Order("Alice", OrderStatus.CONFIRMED);
    order.setTotalAmount(new BigDecimal("1999.98"));

    when(orderService.createOrder(any())).thenReturn(order);

    mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
}
```

> **Design Decision:** The tests mock `ProductServiceClient` and `InventoryServiceClient`. This is fast and deterministic, but **fragile**: if the real product-service changes its response format, our mock won't catch it. This is where **contract tests** (Pact or Spring Cloud Contract) fill the gap — they verify both sides agree on the API shape. We don't implement contract tests in this lab due to time constraints, but architects should plan for them.

---

## Verification
```bash
cd services/order-service && mvn test
```
All tests should pass: 4 service tests + 4 controller tests + 2 client tests.

## Discussion Questions
1. What happens if `cancelReservation` itself fails during compensation? How would you handle that?
2. When would you switch from synchronous orchestration (our approach) to event-driven choreography (saga pattern)?
3. How fragile are our mocked client tests? What would break silently?

## Common Pitfalls
- **Mock setup order matters:** Mockito stubs are matched in order — put specific matchers before general ones
- **Forgetting to verify compensation:** Always assert that cleanup happened on failure paths
- **Testing the mock, not the behavior:** If you're asserting on mock return values, you're testing Mockito, not your code
