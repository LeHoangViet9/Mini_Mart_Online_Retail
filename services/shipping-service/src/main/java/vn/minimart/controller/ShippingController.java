package vn.minimart.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.minimart.dto.*;
import vn.minimart.service.ShippingService;

@RestController
public class ShippingController {
    private final ShippingService service;

    public ShippingController(ShippingService service) {
        this.service = service;
    }

    @PutMapping("/api/shipping/rejection")
    public ShippingSettingsResponse settings(@Valid @RequestBody ShippingSettingsRequest request) {
        return service.settings(request);
    }

    @PostMapping("/internal/shipments")
    public ResponseEntity<?> create(@Valid @RequestBody ShipmentRequest request) {
        var result = service.create(request);
        if (!result.accepted()) {
            return ResponseEntity.unprocessableEntity().body(new ShippingRejectionResponse(result.reason()));
        }
        return ResponseEntity.ok(new ShipmentResponse(result.orderId(), "CREATED"));
    }
}
