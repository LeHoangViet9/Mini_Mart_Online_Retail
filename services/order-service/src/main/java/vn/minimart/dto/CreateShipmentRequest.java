package vn.minimart.dto;

import java.util.UUID;
import vn.minimart.model.Zone;
public record CreateShipmentRequest(UUID orderId, String address, Zone zone) {}
