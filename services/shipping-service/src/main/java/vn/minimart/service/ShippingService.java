package vn.minimart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.minimart.dto.*;
import vn.minimart.model.*;
import vn.minimart.repository.ShippingRepository;

@Service
public class ShippingService {
    private static final Logger log = LoggerFactory.getLogger("vn.minimart.ShippingController");
    private final ShippingRepository repository;

    public ShippingService(ShippingRepository repository) {
        this.repository = repository;
    }

    public ShippingSettingsResponse settings(ShippingSettingsRequest request) {
        repository.saveSettings(new ShippingSettings(request.rejectOutsideZone()));
        return new ShippingSettingsResponse(request.rejectOutsideZone());
    }

    @Transactional
    public ShippingResult create(ShipmentRequest request) {
        var settings = repository.findSettings();
        if (settings.rejectOutsideZone() && request.zone() == Zone.OUTSIDE) {
            log.warn("Shipping rejected orderId={} reason=OUTSIDE_DELIVERY_ZONE", request.orderId());
            return new ShippingResult(request.orderId(), false, "OUTSIDE_DELIVERY_ZONE");
        }
        repository.insertShipmentIfAbsent(new Shipment(request.orderId(), request.address(), request.zone()));
        log.info("Shipment created orderId={}", request.orderId());
        return new ShippingResult(request.orderId(), true, null);
    }
}
