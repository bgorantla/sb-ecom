package com.ecommerce.project.service;

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;

public interface OrderService {
    /**
     * Turns the user's cart into an order once Stripe has confirmed the payment.
     * Safe to call twice for the same payment: the second call returns the existing order.
     * Throws OrderNotFulfillableException if the order can't be created, so the caller can refund.
     */
    OrderDTO placePaidOrder(String emailId, Long addressId, String paymentIntentId, long amountPaidInCents);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder(Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
