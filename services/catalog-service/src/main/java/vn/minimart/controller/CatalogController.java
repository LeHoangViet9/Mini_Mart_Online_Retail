package vn.minimart.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.minimart.dto.*;
import vn.minimart.service.CatalogService;

@RestController
public class CatalogController {
    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    @PostMapping("/internal/reservations")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return service.reserve(request);
    }

    @PostMapping("/internal/reservations/{id}/release")
    public void release(@PathVariable UUID id) {
        service.release(id);
    }

    @GetMapping("/api/products/{id}")
    public ProductResponse product(@PathVariable long id) {
        return service.product(id);
    }
}
