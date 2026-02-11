# Module 3: TDD Exercise — Inventory Service


## Learning Objectives
- Apply TDD to richer business logic (stock reservation, idempotency)
- Experience how TDD naturally surfaces non-obvious requirements
- Write Testcontainers-based integration tests against real PostgreSQL
- Implement optimistic locking driven by a failing test

## Prerequisites
- Module 2 complete (product-service built via TDD)
- Docker Desktop running (required for Testcontainers)

---

## Concepts

The inventory service has the richest business logic in our system: reserving stock must be **atomic** (no partial reservations), **idempotent** (same request twice yields same result), and **safe under concurrency** (two simultaneous reservations can't oversell). These requirements emerge naturally through TDD — each new test reveals a requirement the previous implementation didn't handle.

---

## Exercise 3a: Stock Reservation Business Logic (20 min)

### Step 1: RED — Reserve stock decrements available, increments reserved

```java
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private StockItemRepository stockItemRepository;
    @Mock private ReservationRepository reservationRepository;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(stockItemRepository, reservationRepository);
    }

    @Test
    void should_ReserveStock_When_SufficientQuantityAvailable() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        StockItem stockItem = new StockItem(productId, 100);

        when(stockItemRepository.findByProductId(productId))
            .thenReturn(Optional.of(stockItem));
        when(reservationRepository.findByStockItemIdAndOrderId(any(), eq(orderId)))
            .thenReturn(Optional.empty());
        when(reservationRepository.save(any(Reservation.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var request = new ReserveStockRequest(productId, orderId, 10);
        Reservation result = inventoryService.reserveStock(request);

        assertThat(stockItem.getQuantityAvailable()).isEqualTo(90);
        assertThat(stockItem.getQuantityReserved()).isEqualTo(10);
        assertThat(result.getQuantity()).isEqualTo(10);
    }
}
```

### Step 2: GREEN — Implement `InventoryService.reserveStock()`

The minimal implementation: find stock item, call `stockItem.reserveStock(quantity)`, create and save a `Reservation`.

### Step 3: RED — Insufficient stock throws exception

```java
@Test
void should_ThrowInsufficientStockException_When_NotEnoughStock() {
    UUID productId = UUID.randomUUID();
    StockItem stockItem = new StockItem(productId, 5);

    when(stockItemRepository.findByProductId(productId))
        .thenReturn(Optional.of(stockItem));
    when(reservationRepository.findByStockItemIdAndOrderId(any(), any()))
        .thenReturn(Optional.empty());

    var request = new ReserveStockRequest(productId, UUID.randomUUID(), 10);

    assertThatThrownBy(() -> inventoryService.reserveStock(request))
        .isInstanceOf(InsufficientStockException.class);
}
```

### Step 4: GREEN — Add guard clause in `StockItem.reserveStock()`

> **Design Decision:** The business rule lives in the **entity** (`StockItem.reserveStock()` throws if quantity > available), not in the service. This is the Domain-Driven Design principle of putting behavior where the data lives. The service orchestrates, the entity enforces.

### Step 5: RED — Idempotency: same orderId twice returns existing reservation

```java
@Test
void should_ReturnExistingReservation_When_DuplicateRequest() {
    UUID productId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    StockItem stockItem = new StockItem(productId, 100);
    Reservation existing = new Reservation(stockItem.getId(), orderId, 10);

    when(stockItemRepository.findByProductId(productId))
        .thenReturn(Optional.of(stockItem));
    when(reservationRepository.findByStockItemIdAndOrderId(stockItem.getId(), orderId))
        .thenReturn(Optional.of(existing));

    var request = new ReserveStockRequest(productId, orderId, 10);
    Reservation result = inventoryService.reserveStock(request);

    assertThat(result).isSameAs(existing);
    // Stock should NOT be decremented again
    assertThat(stockItem.getQuantityAvailable()).isEqualTo(100);
    verify(reservationRepository, never()).save(any());
}
```

### Step 6: GREEN — Add idempotency check before reservation logic

> **Design Decision:** TDD naturally surfaced the idempotency requirement. In a distributed system, the order-service might retry a failed reservation call. Without idempotency, retries would double-reserve stock. The test **forced** us to think about this because we had to decide: what happens if reserveStock is called twice with the same orderId?

### Step 7: RED/GREEN — Cancel reservation restores stock

```java
@Test
void should_ReleaseStock_When_ReservationCancelled() {
    UUID reservationId = UUID.randomUUID();
    UUID stockItemId = UUID.randomUUID();
    StockItem stockItem = new StockItem(UUID.randomUUID(), 90);
    // Simulate 10 units already reserved
    Reservation reservation = new Reservation(stockItemId, UUID.randomUUID(), 10);

    when(reservationRepository.findById(reservationId))
        .thenReturn(Optional.of(reservation));
    when(stockItemRepository.findById(reservation.getStockItemId()))
        .thenReturn(Optional.of(stockItem));

    inventoryService.cancelReservation(reservationId);

    assertThat(stockItem.getQuantityAvailable()).isEqualTo(100);
    assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
}
```

---

## Exercise 3b: Integration Tests with Testcontainers (15 min)

### Step 1: RED — Reservation persists across service restart

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventoryRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("inventory_db");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private StockItemRepository stockItemRepository;

    @Test
    void should_PersistAndRetrieveStockItem() {
        StockItem item = new StockItem(UUID.randomUUID(), 100);
        StockItem saved = stockItemRepository.save(item);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();

        Optional<StockItem> found = stockItemRepository.findByProductId(item.getProductId());
        assertThat(found).isPresent();
        assertThat(found.get().getQuantityAvailable()).isEqualTo(100);
    }
}
```

> **Design Decision:** We use Testcontainers (real PostgreSQL) instead of H2 because: (1) PostgreSQL-specific types like `gen_random_uuid()` don't work in H2, (2) locking behavior differs, (3) constraint enforcement differs. For architects: always test against the real database engine. The 3-second startup cost of Testcontainers is worth the production parity.

### Step 2: RED — Optimistic locking prevents concurrent overselling

```java
@Test
void should_EnforceOptimisticLocking() {
    StockItem item = stockItemRepository.save(new StockItem(UUID.randomUUID(), 100));

    // Simulate two concurrent reads
    StockItem read1 = stockItemRepository.findById(item.getId()).orElseThrow();
    StockItem read2 = stockItemRepository.findById(item.getId()).orElseThrow();

    // First write succeeds
    read1.reserveStock(10);
    stockItemRepository.saveAndFlush(read1);

    // Second write should fail — stale version
    read2.reserveStock(10);
    assertThatThrownBy(() -> stockItemRepository.saveAndFlush(read2))
        .isInstanceOf(Exception.class); // OptimisticLockException or wrapper
}
```

### Step 3: GREEN — Add `@Version` field to `StockItem`, handle in service

> **Design Decision:** Optimistic locking vs. pessimistic locking — optimistic is the right choice here because stock reservation conflicts are rare (most products aren't being reserved simultaneously). Pessimistic locking (`SELECT ... FOR UPDATE`) would serialize all reservations, killing throughput. The `@Version` field detects conflicts without holding database locks.

---

## Verification
```bash
cd services/inventory-service && mvn test
```
All tests should pass: 6 unit tests + 3 integration tests (integration tests need Docker running).

## Discussion Questions
1. How did writing the idempotency test change the service implementation? Would you have thought of it without TDD?
2. When would pessimistic locking be the better architectural choice?
3. How would you test the stock reservation under realistic concurrent load?

## Common Pitfalls
- **Testcontainers Docker not running:** Ensure Docker Desktop is started before running integration tests
- **Flyway migration fails:** Verify your SQL migration matches your JPA entity exactly
- **Optimistic lock test flaky:** Use `saveAndFlush()` instead of `save()` to force immediate write
