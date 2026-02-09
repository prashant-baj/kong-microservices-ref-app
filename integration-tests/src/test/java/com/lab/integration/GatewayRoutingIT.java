package com.lab.integration;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests that Kong correctly routes requests to the appropriate microservices.
 *
 * Prerequisites: docker compose up --build
 * Run with: mvn verify -pl integration-tests -Dgateway.url=http://localhost:8000
 */
class GatewayRoutingIT {

    @BeforeAll
    static void setUp() {
        String gatewayUrl = System.getProperty("gateway.url", "http://localhost:8000");
        RestAssured.baseURI = gatewayUrl;
    }

    @Test
    void should_RouteToProductService() {
        given()
        .when()
                .get("/api/products")
        .then()
                .statusCode(200)
                .header("Via", containsString("kong"));
    }

    @Test
    void should_RouteToInventoryService() {
        given()
        .when()
                .get("/api/inventory/stock/00000000-0000-0000-0000-000000000000")
        .then()
                .statusCode(anyOf(is(200), is(404)))
                .header("Via", containsString("kong"));
    }

    @Test
    void should_RequireApiKeyForOrders() {
        given()
        .when()
                .get("/api/orders")
        .then()
                .statusCode(401);
    }

    @Test
    void should_AllowOrdersWithApiKey() {
        given()
                .header("apikey", "lab-api-key-2024")
        .when()
                .get("/api/orders")
        .then()
                .statusCode(200)
                .header("Via", containsString("kong"));
    }

    @Test
    void should_Return404_ForUnknownRoutes() {
        given()
        .when()
                .get("/api/unknown")
        .then()
                .statusCode(404);
    }
}
