package vn.minimart.model;

import java.util.UUID;
public record Reservation(UUID orderId, long productId, int quantity, long amount, boolean released) {}
