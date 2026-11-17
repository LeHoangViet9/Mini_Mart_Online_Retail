package vn.minimart.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.minimart.dto.*;
import vn.minimart.model.Reservation;
import vn.minimart.repository.CatalogRepository;
import static org.springframework.http.HttpStatus.*;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger("vn.minimart.CatalogController");
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ReservationResponse reserve(ReserveRequest request) {
        var product = repository.lockProduct(request.productId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "PRODUCT_NOT_FOUND"));
        var old = repository.findReservation(request.orderId());
        if (old.isPresent()) {
            var saved = old.get();
            if (saved.productId() != request.productId() || saved.quantity() != request.quantity() || saved.released()) {
                throw new ResponseStatusException(CONFLICT, "RESERVATION_CONFLICT");
            }
            return new ReservationResponse(saved.amount());
        }
        if (product.stock() < request.quantity()) {
            throw new ResponseStatusException(CONFLICT, "INSUFFICIENT_STOCK");
        }
        long amount = Math.multiplyExact(product.price(), request.quantity());
        repository.changeStock(request.productId(), -request.quantity());
        repository.insertReservation(new Reservation(request.orderId(), request.productId(), request.quantity(), amount, false));
        log.info("Inventory reserved orderId={}", request.orderId());
        return new ReservationResponse(amount);
    }

    @Transactional
    public void release(UUID id) {
        var old = repository.lockReservation(id);
        if (old.isEmpty() || old.get().released()) {
            return;
        }
        var reservation = old.get();
        repository.changeStock(reservation.productId(), reservation.quantity());
        repository.setReleased(id, true);
    }

    public ProductResponse product(long id) {
        return ProductResponse.from(repository.findProduct(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "PRODUCT_NOT_FOUND")));
    }
}
