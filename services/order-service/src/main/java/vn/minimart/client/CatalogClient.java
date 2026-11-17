package vn.minimart.client;

import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.minimart.dto.ReservationResponse;
import vn.minimart.dto.ReserveStockRequest;

@Component
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    public long reserve(UUID orderId, long productId, int quantity) {
        var response = client.post().uri("/internal/reservations")
                .body(new ReserveStockRequest(orderId, productId, quantity)).retrieve().body(ReservationResponse.class);
        return Objects.requireNonNull(response).amount().longValue();
    }

    public void release(UUID orderId) {
        client.post().uri("/internal/reservations/{id}/release", orderId).retrieve().toBodilessEntity();
    }
}
