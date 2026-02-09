package com.lab.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.order.client.InventoryServiceClient.ReservationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class InventoryServiceClientTest {

    private InventoryServiceClient inventoryServiceClient;
    private MockRestServiceServer mockServer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        inventoryServiceClient = new InventoryServiceClient(builder, "http://localhost:8082");
    }

    @Test
    void should_ReturnReservationInfo_When_StockReserved() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        var expectedReservation = new ReservationInfo(reservationId, productId, 5);

        mockServer.expect(requestTo("http://localhost:8082/api/inventory/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        objectMapper.writeValueAsString(expectedReservation),
                        MediaType.APPLICATION_JSON
                ));

        ReservationInfo result = inventoryServiceClient.reserveStock(productId, orderId, 5);

        assertThat(result.id()).isEqualTo(reservationId);
        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.quantity()).isEqualTo(5);

        mockServer.verify();
    }

    @Test
    void should_CallDeleteEndpoint_When_CancellingReservation() {
        UUID reservationId = UUID.randomUUID();

        mockServer.expect(requestTo("http://localhost:8082/api/inventory/reservations/" + reservationId))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        inventoryServiceClient.cancelReservation(reservationId);

        mockServer.verify();
    }
}
