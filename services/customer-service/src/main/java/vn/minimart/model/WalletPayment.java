package vn.minimart.model;

import java.util.UUID;
public record WalletPayment(UUID orderId, long customerId, long amount, boolean refunded) {}
