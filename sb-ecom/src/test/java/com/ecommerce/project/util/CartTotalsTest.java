package com.ecommerce.project.util;

import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A plain unit test: no Spring, no database, runs in milliseconds. */
class CartTotalsTest {

    @Test
    void addsUpPriceTimesQuantityInCents() {
        Cart cart = cartOf(item(19.99, 3), item(45.50, 1));

        assertThat(CartTotals.inCents(cart)).isEqualTo(10_547L);
    }

    @Test
    void roundsToTheNearestCentInsteadOfCuttingOff() {
        // 19.99 × 3 is 59.969999999999999 as a double. Cutting off would charge 5996 cents.
        Cart cart = cartOf(item(19.99, 3));

        assertThat(CartTotals.inCents(cart)).isEqualTo(5_997L);
    }

    @Test
    void manySmallPricesDoNotDrift() {
        // 0.1 can't be stored exactly in a double; a hundred of them must still be $10.00
        CartItem[] items = new CartItem[100];
        for (int i = 0; i < items.length; i++) {
            items[i] = item(0.10, 1);
        }

        assertThat(CartTotals.inCents(cartOf(items))).isEqualTo(1_000L);
    }

    @Test
    void anEmptyCartCostsNothing() {
        assertThat(CartTotals.inCents(new Cart())).isZero();
    }

    private static Cart cartOf(CartItem... items) {
        Cart cart = new Cart();
        for (CartItem item : items) {
            cart.getCartItems().add(item);
        }
        return cart;
    }

    private static CartItem item(double price, int quantity) {
        CartItem item = new CartItem();
        item.setProductPrice(price);
        item.setQuantity(quantity);
        return item;
    }
}
