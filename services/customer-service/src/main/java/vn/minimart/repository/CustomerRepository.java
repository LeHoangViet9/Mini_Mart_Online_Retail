package vn.minimart.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import vn.minimart.model.Customer;
import vn.minimart.model.WalletPayment;

@Repository
public class CustomerRepository {
    private static final RowMapper<Customer> CUSTOMER = (rs, row) -> new Customer(
            rs.getLong("id"), rs.getString("name"), rs.getLong("balance"));
    private static final RowMapper<WalletPayment> PAYMENT = (rs, row) -> new WalletPayment(
            rs.getObject("order_id", UUID.class), rs.getLong("customer_id"), rs.getLong("amount"), rs.getBoolean("refunded"));
    private final JdbcTemplate db;

    public CustomerRepository(JdbcTemplate db) {
        this.db = db;
    }

    public Optional<Customer> findCustomer(long id) {
        return db.query("SELECT * FROM customers WHERE id=?", CUSTOMER, id).stream().findFirst();
    }

    public Optional<Customer> lockCustomer(long id) {
        return db.query("SELECT * FROM customers WHERE id=? FOR UPDATE", CUSTOMER, id).stream().findFirst();
    }

    public Optional<WalletPayment> findPayment(UUID id) {
        return db.query("SELECT * FROM wallet_payments WHERE order_id=?", PAYMENT, id).stream().findFirst();
    }

    public Optional<WalletPayment> lockPayment(UUID id) {
        return db.query("SELECT * FROM wallet_payments WHERE order_id=? FOR UPDATE", PAYMENT, id).stream().findFirst();
    }

    public void changeBalance(long customerId, long delta) {
        db.update("UPDATE customers SET balance=balance+? WHERE id=?", delta, customerId);
    }

    public void insertPayment(WalletPayment payment) {
        db.update("INSERT INTO wallet_payments(order_id,customer_id,amount,refunded) VALUES(?,?,?,?)",
                payment.orderId(), payment.customerId(), payment.amount(), payment.refunded());
    }

    public void setRefunded(UUID id, boolean refunded) {
        db.update("UPDATE wallet_payments SET refunded=? WHERE order_id=?", refunded, id);
    }
}
