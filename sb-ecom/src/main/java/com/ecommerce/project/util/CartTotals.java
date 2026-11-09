package com.ecommerce.project.util;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;

/** The amount to charge for a cart, worked out from the cart items in the database. */
public final class CartTotals {

    private CartTotals() {
    }

    /** Stripe works in the smallest currency unit, so $12.34 becomes 1234. */
    public static long inCents(Cart cart) {
        double total = 0;
        for (CartItem item : cart.getCartItems()) {
            total += item.getProductPrice() * item.getQuantity();
        }
        return Math.round(total * 100);
    }
}
