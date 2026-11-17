package vn.minimart.dto;

import jakarta.validation.constraints.*;
import vn.minimart.model.Zone;
public record PlaceOrderRequest(@Positive long customerId, @Positive long productId,
                                @Min(1) @Max(100) int quantity,
                                @NotBlank @Size(max=300) String address, @NotNull Zone zone) {}
