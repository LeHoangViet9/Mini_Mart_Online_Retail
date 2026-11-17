package vn.minimart.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.minimart.dto.*;
import vn.minimart.model.WalletPayment;
import vn.minimart.repository.CustomerRepository;
import static org.springframework.http.HttpStatus.*;

@Service
public class CustomerService {
    private static final Logger log = LoggerFactory.getLogger("vn.minimart.CustomerController");
    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void debit(PaymentRequest request) {
        var customer = repository.lockCustomer(request.customerId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "CUSTOMER_NOT_FOUND"));
        var old = repository.findPayment(request.orderId());
        if (old.isPresent()) {
            var saved = old.get();
            if (saved.customerId() != request.customerId() || saved.amount() != request.amount() || saved.refunded()) {
                throw new ResponseStatusException(CONFLICT, "PAYMENT_CONFLICT");
            }
            return;
        }
        if (customer.balance() < request.amount()) {
            throw new ResponseStatusException(CONFLICT, "INSUFFICIENT_BALANCE");
        }
        repository.changeBalance(request.customerId(), -request.amount());
        repository.insertPayment(new WalletPayment(request.orderId(), request.customerId(), request.amount(), false));
        log.info("Wallet debited orderId={} amount={}", request.orderId(), request.amount());
    }

    @Transactional
    public void refund(UUID id) {
        var payment = repository.lockPayment(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "PAYMENT_NOT_FOUND"));
        if (!payment.refunded()) {
            repository.changeBalance(payment.customerId(), payment.amount());
            repository.setRefunded(id, true);
        }
        log.info("Wallet refunded orderId={}", id);
    }

    public CustomerResponse customer(long id) {
        return CustomerResponse.from(repository.findCustomer(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "CUSTOMER_NOT_FOUND")));
    }
}
