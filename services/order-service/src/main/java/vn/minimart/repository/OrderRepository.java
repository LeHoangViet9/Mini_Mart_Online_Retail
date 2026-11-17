package vn.minimart.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.minimart.model.*;

@Repository
public class OrderRepository {
    private static final RowMapper<Order> ORDER = (rs, row) -> new Order(
            rs.getObject("id", UUID.class), rs.getLong("customer_id"), rs.getLong("product_id"),
            rs.getInt("quantity"), rs.getString("address"), Zone.valueOf(rs.getString("zone")),
            OrderStatus.valueOf(rs.getString("status")), rs.getLong("amount"), rs.getBoolean("refunded"),
            rs.getString("reason"), rs.getString("trace_id"));
    private final JdbcTemplate db;

    public OrderRepository(JdbcTemplate db) {
        this.db = db;
    }

    public void insert(Order order) {
        db.update("INSERT INTO orders(id,customer_id,product_id,quantity,address,zone,status,amount,refunded,reason,trace_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                order.id(), order.customerId(), order.productId(), order.quantity(), order.address(),
                order.zone().name(), order.status().name(), order.amount(), order.refunded(), order.reason(), order.traceId());
    }

    public void updateAmount(UUID id, long amount) {
        db.update("UPDATE orders SET amount=? WHERE id=?", amount, id);
    }

    public void updateOutcome(UUID id, OrderStatus status, boolean refunded, String reason) {
        db.update("UPDATE orders SET status=?,refunded=?,reason=? WHERE id=?", status.name(), refunded, reason, id);
    }

    public Optional<Order> find(UUID id) {
        return db.query("SELECT * FROM orders WHERE id=?", ORDER, id).stream().findFirst();
    }
}
