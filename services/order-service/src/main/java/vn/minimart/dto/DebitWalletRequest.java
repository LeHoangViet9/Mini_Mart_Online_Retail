package vn.minimart.dto;

import java.util.UUID;
public record DebitWalletRequest(UUID orderId, long customerId, long amount) {}
