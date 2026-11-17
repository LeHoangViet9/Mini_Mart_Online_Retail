package vn.minimart.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
public record PaymentRequest(@NotNull UUID orderId, @Positive long customerId, @Positive long amount) {}
