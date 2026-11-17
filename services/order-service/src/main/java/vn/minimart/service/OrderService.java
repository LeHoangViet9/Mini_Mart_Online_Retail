package vn.minimart.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import vn.minimart.client.*;
import vn.minimart.dto.*;
import vn.minimart.model.*;
import vn.minimart.repository.OrderRepository;
import static org.springframework.http.HttpStatus.*;

@Service
public class OrderService {
    // Retain the original logger name for existing log queries and dashboards.
    private static final Logger log = LoggerFactory.getLogger("vn.minimart.OrderController");
    private final OrderRepository repository;
    private final CatalogClient catalog;
    private final CustomerClient customer;
    private final ShippingClient shipping;

    public OrderService(OrderRepository repository, CatalogClient catalog, CustomerClient customer, ShippingClient shipping) {
        this.repository = repository;
        this.catalog = catalog;
        this.customer = customer;
        this.shipping = shipping;
    }

    // No transaction spans HTTP: PENDING is committed before the first remote call.
    public OrderResponse place(PlaceOrderRequest request) {
        UUID id = UUID.randomUUID();
        repository.insert(new Order(id, request.customerId(), request.productId(), request.quantity(),
                request.address(), request.zone(), OrderStatus.PENDING, 0, false, null, MDC.get("trace.id")));
        log.info("Order PENDING orderId={}", id);
        boolean reserved = false, debited = false;
        try {
            long amount = catalog.reserve(id, request.productId(), request.quantity());
            reserved = true;
            repository.updateAmount(id, amount);
            customer.debit(id, request.customerId(), amount);
            debited = true;
            try {
                shipping.create(id, request.address(), request.zone());
            } catch (HttpClientErrorException.UnprocessableEntity exception) {
                log.warn("Order cancellation caused by shipping rejection orderId={}", id, exception);
                customer.refund(id);
                catalog.release(id);
                repository.updateOutcome(id, OrderStatus.CANCELLED, true, "OUTSIDE_DELIVERY_ZONE");
                return get(id);
            }
            repository.updateOutcome(id, OrderStatus.CONFIRMED, false, null);
            log.info("Order CONFIRMED orderId={}", id);
            return get(id);
        } catch (RestClientException exception) {
            // Preserve existing handling of definite rejection versus uncertain results.
            if (!debited && exception instanceof HttpClientErrorException) {
                if (reserved) {
                    catalog.release(id);
                }
                repository.updateOutcome(id, OrderStatus.CANCELLED, false, "BUSINESS_REJECTED");
                throw new ResponseStatusException(CONFLICT, "BUSINESS_REJECTED orderId=" + id);
            }
            log.error("Order needs reconciliation; retained PENDING orderId={}", id, exception);
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "RECONCILIATION_REQUIRED orderId=" + id);
        }
    }

    public OrderResponse get(UUID id) {
        return OrderResponse.from(repository.find(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "ORDER_NOT_FOUND")));
    }
}
