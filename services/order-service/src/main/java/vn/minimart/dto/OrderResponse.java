package vn.minimart.dto;

import java.util.UUID;
import vn.minimart.model.Order;
public record OrderResponse(UUID id, String status, long amount, boolean refunded, String reason, String traceId) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status().name(), order.amount(),
                order.refunded(), order.reason(), order.traceId());
    }
}
