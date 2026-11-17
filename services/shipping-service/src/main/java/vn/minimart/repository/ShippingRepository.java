package vn.minimart.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.minimart.model.Shipment;
import vn.minimart.model.ShippingSettings;

@Repository
public class ShippingRepository {
    private final JdbcTemplate db;

    public ShippingRepository(JdbcTemplate db) {
        this.db = db;
    }

    public ShippingSettings findSettings() {
        return db.queryForObject("SELECT reject_outside FROM shipping_settings WHERE id=1",
                (rs, row) -> new ShippingSettings(rs.getBoolean("reject_outside")));
    }

    public void saveSettings(ShippingSettings settings) {
        db.update("UPDATE shipping_settings SET reject_outside=? WHERE id=1", settings.rejectOutsideZone());
    }

    public void insertShipmentIfAbsent(Shipment shipment) {
        db.update("INSERT INTO shipments(order_id,address,zone) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM shipments WHERE order_id=?)",
                shipment.orderId(), shipment.address(), shipment.zone().name(), shipment.orderId());
    }
}
