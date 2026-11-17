package vn.minimart.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.minimart.dto.CreateShipmentRequest;
import vn.minimart.model.Zone;

@Component
public class ShippingClient {
    private final RestClient client;

    public ShippingClient(@Qualifier("shippingRestClient") RestClient client) {
        this.client = client;
    }

    public void create(UUID orderId, String address, Zone zone) {
        client.post().uri("/internal/shipments").body(new CreateShipmentRequest(orderId, address, zone))
                .retrieve().toBodilessEntity();
    }
}
