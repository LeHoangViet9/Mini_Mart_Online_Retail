package vn.minimart.client;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.minimart.dto.DebitWalletRequest;

@Component
public class CustomerClient {
    private final RestClient client;

    public CustomerClient(@Qualifier("customerRestClient") RestClient client) {
        this.client = client;
    }

    public void debit(UUID orderId, long customerId, long amount) {
        client.post().uri("/internal/payments").body(new DebitWalletRequest(orderId, customerId, amount))
                .retrieve().toBodilessEntity();
    }

    public void refund(UUID orderId) {
        client.post().uri("/internal/payments/{id}/refund", orderId).retrieve().toBodilessEntity();
    }
}
