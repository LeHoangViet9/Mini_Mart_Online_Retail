package vn.minimart.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.minimart.dto.*;
import vn.minimart.service.CustomerService;

@RestController
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping("/internal/payments")
    public void debit(@Valid @RequestBody PaymentRequest request) {
        service.debit(request);
    }

    @PostMapping("/internal/payments/{id}/refund")
    public void refund(@PathVariable UUID id) {
        service.refund(id);
    }

    @GetMapping("/api/customers/{id}")
    public CustomerResponse customer(@PathVariable long id) {
        return service.customer(id);
    }
}
