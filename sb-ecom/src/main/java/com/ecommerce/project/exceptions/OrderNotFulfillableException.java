package com.ecommerce.project.exceptions;

/**
 * The shopper has paid, but the order can't be created (the cart changed, or an item sold out
 * while they were paying). CheckoutService catches this and refunds the payment.
 */
public class OrderNotFulfillableException extends APIException {

    public OrderNotFulfillableException(String message) {
        super(message);
    }
}
