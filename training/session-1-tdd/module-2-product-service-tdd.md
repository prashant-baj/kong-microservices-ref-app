# Module 2: TDD Exercise — Product Service

## Duration
35 minutes

## Learning Objectives
- Apply strict Red-Green-Refactor to build a service layer from scratch
- Use MockMvc to test-drive a REST API with proper status codes and error responses
- Experience how tests drive interface design decisions

## Prerequisites
- Module 1 complete
- IDE open with the project loaded
- Familiarity with JUnit 5, Mockito, Spring Boot test slices

---

## Exercise 2a: Domain and Service Layer via Unit Tests (20 min)

### Step 1: RED — Write the first failing test

Before writing any production code, write a test that describes what `createProduct()` should do:

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void should_CreateProduct_When_NameIsUnique() {
        var request = new CreateProductRequest(
            "Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");

        when(productRepository.existsByName("Laptop")).thenReturn(false);
        when(productRepository.save(any(Product.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(request);

        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
    }
}
```

This test doesn't compile yet — `ProductService`, `CreateProductRequest`, `ProductRepository`, and `Product` don't exist. That's the point.

> **Design Decision:** Notice how the test forced us to decide: the service takes a `CreateProductRequest` (not a raw entity), returns a `Product`, and checks uniqueness by name. These are API contract decisions driven by the test.

### Step 2: GREEN — Make it compile and pass

Create the minimum code to make the test pass:
1. `Product` entity with fields: name, description, price, category
2. `CreateProductRequest` record with validation annotations
3. `ProductRepository` interface extending `JpaRepository`
4. `ProductService` with `createProduct()` method

The implementation should be the simplest thing that makes the test green.

### Step 3: RED — Duplicate name detection

```java
@Test
void should_ThrowDuplicateException_When_NameAlreadyExists() {
    var request = new CreateProductRequest(
        "Laptop", "Another laptop", new BigDecimal("499.99"), "Electronics");

    when(productRepository.existsByName("Laptop")).thenReturn(true);

    assertThatThrownBy(() -> productService.createProduct(request))
            .isInstanceOf(DuplicateProductException.class)
            .hasMessageContaining("Laptop");

    verify(productRepository, never()).save(any());
}
```

### Step 4: GREEN — Implement the guard clause

Add the uniqueness check to `createProduct()` and create `DuplicateProductException`.

### Step 5: RED — Retrieve by ID (happy + sad paths)

```java
@Test
void should_ReturnProduct_When_IdExists() {
    UUID id = UUID.randomUUID();
    Product product = new Product("Laptop", "A laptop", new BigDecimal("999.99"), "Electronics");
    when(productRepository.findById(id)).thenReturn(Optional.of(product));

    Product result = productService.getProduct(id);
    assertThat(result.getName()).isEqualTo("Laptop");
}

@Test
void should_ThrowNotFoundException_When_IdDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(productRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> productService.getProduct(id))
            .isInstanceOf(ProductNotFoundException.class);
}
```

### Step 6: GREEN — Implement retrieval with proper error handling

> **Design Decision:** We chose to throw exceptions for "not found" rather than returning `Optional` from the service. This pushes error handling to the controller layer via `@RestControllerAdvice`, keeping the service layer focused on business logic.

---

## Exercise 2b: REST API Layer via MockMvc Tests (15 min)

### Step 1: RED — POST endpoint returns 201 with Location header

```java
@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private ProductService productService;

    @Test
    void should_Return201WithLocation_When_ProductCreated() throws Exception {
        var request = new CreateProductRequest(
            "Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");
        var product = new Product("Laptop", "A powerful laptop",
            new BigDecimal("999.99"), "Electronics");

        when(productService.createProduct(any())).thenReturn(product);

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.name").value("Laptop"));
    }
}
```

### Step 2: GREEN — Implement `ProductController`

### Step 3: RED — Validation errors return 400 with ProblemDetail

```java
@Test
void should_Return400_When_NameIsBlank() throws Exception {
    var request = new CreateProductRequest("", "Description",
        new BigDecimal("10.00"), "Category");

    mockMvc.perform(post("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").exists());
}
```

### Step 4: GREEN — Add `@Valid`, Bean Validation annotations, and `@RestControllerAdvice`

### Step 5: RED/GREEN — GET endpoints (200 and 404)

> **Design Decision:** MockMvc tests serve as living API documentation. Each test describes an HTTP interaction: request shape, response status, response body structure. Compare this with OpenAPI-first design — TDD gives you the same contract specification, but with the added benefit of being executable.

---

## Verification
Run the tests:
```bash
cd services/product-service && mvn test
```
All 8 tests should pass (5 service + 3 controller tests).

## Discussion Questions
1. How did writing tests first change the way you thought about the `ProductService` interface?
2. Would you use the same approach for a CRUD service vs. a service with complex business rules?
3. When would you prefer OpenAPI-first over TDD-first for API design?

## Common Pitfalls
- **Over-mocking:** If your test has more mock setup than assertions, you're probably testing the wrong layer
- **Testing framework behavior:** Don't test that Spring's `@Valid` annotation works — test that YOUR validation rules are correct
- **Brittle JSON assertions:** Use `jsonPath` for structure, not exact string matching
