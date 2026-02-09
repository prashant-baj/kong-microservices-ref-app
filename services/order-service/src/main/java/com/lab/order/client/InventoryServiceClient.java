package com.lab.order.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class InventoryServiceClient {

    private final RestClient restClient;

    public InventoryServiceClient(RestClient.Builder restClientBuilder,
                                  @Value("${services.inventory-service.url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public ReservationInfo reserveStock(UUID productId, UUID orderId, int quantity) {
        var payload = new ReserveStockPayload(productId, orderId, quantity);
        return restClient.post()
                .uri("/api/inventory/reservations")
                .body(payload)
                .retrieve()
                .body(ReservationInfo.class);
    }

    public void cancelReservation(UUID reservationId) {
        restClient.delete()
                .uri("/api/inventory/reservations/{id}", reservationId)
                .retrieve()
                .toBodilessEntity();
    }

    public record ReservationInfo(UUID id, UUID productId, int quantity) {}

    public record ReserveStockPayload(UUID productId, UUID orderId, int quantity) {}
}
