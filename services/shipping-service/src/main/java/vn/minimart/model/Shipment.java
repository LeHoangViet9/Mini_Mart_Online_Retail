package vn.minimart.model;

import java.util.UUID;
public record Shipment(UUID orderId, String address, Zone zone) {}
