package com.ecommerce.project.payload;

/** Body of POST /api/order/stripe/confirm: the payment the browser just completed with Stripe. */
public record ConfirmPaymentRequest(String paymentIntentId) {
}
