package vn.minimart.dto;

import vn.minimart.model.Product;
public record ProductResponse(long id, String name, long price, int stock) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.price(), product.stock());
    }
}
