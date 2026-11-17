package vn.minimart.dto;

import vn.minimart.model.Customer;
public record CustomerResponse(long id, String name, long balance) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.id(), customer.name(), customer.balance());
    }
}
