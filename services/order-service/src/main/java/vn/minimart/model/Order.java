package vn.minimart.model;

import java.util.UUID;
public record Order(UUID id, long customerId, long productId, int quantity, String address, Zone zone,
                    OrderStatus status, long amount, boolean refunded, String reason, String traceId) {}
