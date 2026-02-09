package com.lab.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.order.client.ProductServiceClient.ProductInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductServiceClientTest {

    private ProductServiceClient productServiceClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        productServiceClient = new ProductServiceClient(builder, "http://localhost:8081");
    }

    @Test
    void should_ReturnProductInfo_When_ProductExists() throws Exception {
        UUID productId = UUID.randomUUID();
        var expectedProduct = new ProductInfo(productId, "Laptop", new BigDecimal("999.99"));

        mockServer.expect(requestTo("http://localhost:8081/api/products/" + productId))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(expectedProduct),
                        MediaType.APPLICATION_JSON
                ));

        ProductInfo result = productServiceClient.getProduct(productId);

        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.name()).isEqualTo("Laptop");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("999.99"));

        mockServer.verify();
    }
}
