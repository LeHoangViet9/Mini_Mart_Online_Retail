package vn.minimart.model;

import java.util.UUID;
public record ShippingResult(UUID orderId, boolean accepted, String reason) {}
