package com.ecommerce.project.payload;

/** Body of POST /api/order/stripe-client-secret. Only the address: the server works out the amount. */
public record StartPaymentRequest(Long addressId) {
}
