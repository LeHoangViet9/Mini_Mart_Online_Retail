package vn.minimart.dto;

import jakarta.validation.constraints.NotNull;
public record ShippingSettingsRequest(@NotNull Boolean rejectOutsideZone) {}
