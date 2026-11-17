package vn.minimart.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.minimart.dto.OrderResponse;
import vn.minimart.dto.PlaceOrderRequest;
import vn.minimart.service.OrderService;

@RestController
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/api/orders")
    public OrderResponse place(@Valid @RequestBody PlaceOrderRequest request) {
        return service.place(request);
    }

    @GetMapping("/api/orders/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
