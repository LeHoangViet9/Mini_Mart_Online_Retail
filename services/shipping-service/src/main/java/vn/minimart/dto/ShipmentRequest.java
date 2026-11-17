package vn.minimart.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
import vn.minimart.model.Zone;
public record ShipmentRequest(@NotNull UUID orderId, @NotBlank @Size(max=300) String address, @NotNull Zone zone) {}
