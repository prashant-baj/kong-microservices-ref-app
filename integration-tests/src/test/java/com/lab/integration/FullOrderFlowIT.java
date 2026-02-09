package com.lab.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end integration tests that run against the full Docker Compose stack
 * through the Kong API Gateway.
 *
 * Prerequisites: docker compose up --build
 * Run with: mvn verify -pl integration-tests -Dgateway.url=http://localhost:8000
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullOrderFlowIT {

    private static final String API_KEY = "lab-api-key-2024";
    private static String productId;

    @BeforeAll
    static void setUp() {
        String gatewayUrl = System.getProperty("gateway.url", "http://localhost:8000");
        RestAssured.baseURI = gatewayUrl;
    }

    @Test
    @Order(1)
    void should_CreateProduct_Through_Gateway() {
        productId = given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "name", "Integration Test Laptop " + UUID.randomUUID().toString().substring(0, 8),
                        "description", "A laptop for integration testing",
                        "price", 999.99,
                        "category", "Electronics"
                ))
        .when()
                .post("/api/products")
        .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("name", containsString("Integration Test Laptop"))
                .body("price", equalTo(999.99f))
                .extract()
                .jsonPath()
                .getString("id");
    }

    @Test
    @Order(2)
    void should_GetProduct_Through_Gateway() {
        given()
        .when()
                .get("/api/products/{id}", productId)
        .then()
                .statusCode(200)
                .body("id", equalTo(productId));
    }

    @Test
    @Order(3)
    void should_AddStock_Through_Gateway() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "productId", productId,
                        "quantity", 50
                ))
        .when()
                .post("/api/inventory/stock")
        .then()
                .statusCode(201)
                .body("quantityAvailable", equalTo(50));
    }

    @Test
    @Order(4)
    void should_CreateOrder_Through_Gateway_WithApiKey() {
        given()
                .contentType(ContentType.JSON)
                .header("apikey", API_KEY)
                .body(Map.of(
                        "customerName", "Integration Tester",
                        "items", java.util.List.of(
                                Map.of("productId", productId, "quantity", 2)
                        )
                ))
        .when()
                .post("/api/orders")
        .then()
                .statusCode(201)
                .body("customerName", equalTo("Integration Tester"))
                .body("status", equalTo("CONFIRMED"))
                .body("totalAmount", greaterThan(0f));
    }

    @Test
    @Order(5)
    void should_VerifyStockDecremented_After_Order() {
        given()
        .when()
                .get("/api/inventory/stock/{productId}", productId)
        .then()
                .statusCode(200)
                .body("quantityAvailable", equalTo(48))
                .body("quantityReserved", equalTo(2));
    }

    @Test
    @Order(6)
    void should_RejectOrder_Without_ApiKey() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "customerName", "Unauthorized User",
                        "items", java.util.List.of(
                                Map.of("productId", productId, "quantity", 1)
                        )
                ))
        .when()
                .post("/api/orders")
        .then()
                .statusCode(401);
    }
}
