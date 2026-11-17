package vn.minimart.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
public record ReserveRequest(@NotNull UUID orderId, @Positive long productId,
                             @Min(1) @Max(100) int quantity) {}
