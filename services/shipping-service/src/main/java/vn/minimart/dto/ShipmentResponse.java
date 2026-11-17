package vn.minimart.dto;

import java.util.UUID;
public record ShipmentResponse(UUID orderId, String status) {}
