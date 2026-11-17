package vn.minimart.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.minimart.model.Product;
import vn.minimart.model.Reservation;

@Repository
public class CatalogRepository {
    private static final RowMapper<Product> PRODUCT = (rs, row) -> new Product(
            rs.getLong("id"), rs.getString("name"), rs.getLong("price"), rs.getInt("stock"));
    private static final RowMapper<Reservation> RESERVATION = (rs, row) -> new Reservation(
            rs.getObject("order_id", UUID.class), rs.getLong("product_id"), rs.getInt("quantity"),
            rs.getLong("amount"), rs.getBoolean("released"));
    private final JdbcTemplate db;

    public CatalogRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Product> findProduct(long id) {
        return db.query("SELECT * FROM products WHERE id=?", PRODUCT, id).stream().findFirst();
    }

    public Optional<Product> lockProduct(long id) {
        return db.query("SELECT * FROM products WHERE id=? FOR UPDATE", PRODUCT, id).stream().findFirst();
    }

    public Optional<Reservation> findReservation(UUID id) {
        return db.query("SELECT * FROM reservations WHERE order_id=?", RESERVATION, id).stream().findFirst();
    }

    public Optional<Reservation> lockReservation(UUID id) {
        return db.query("SELECT * FROM reservations WHERE order_id=? FOR UPDATE", RESERVATION, id).stream().findFirst();
    }

    public void changeStock(long productId, int delta) {
        db.update("UPDATE products SET stock=stock+? WHERE id=?", delta, productId);
    }

    public void insertReservation(Reservation reservation) {
        db.update("INSERT INTO reservations(order_id,product_id,quantity,amount,released) VALUES(?,?,?,?,?)",
                reservation.orderId(), reservation.productId(), reservation.quantity(),
                reservation.amount(), reservation.released());
    }

    public void setReleased(UUID orderId, boolean released) {
        db.update("UPDATE reservations SET released=? WHERE order_id=?", released, orderId);
    }
}
