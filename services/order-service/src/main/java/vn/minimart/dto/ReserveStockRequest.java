package vn.minimart.dto;

import java.util.UUID;
public record ReserveStockRequest(UUID orderId, long productId, int quantity) {}
