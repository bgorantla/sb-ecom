import { describe, expect, it } from 'vitest';
import { cartReducer } from './cartReducer';

const mug = { productId: 1, productName: 'Mug', quantity: 1 };
const lamp = { productId: 2, productName: 'Lamp', quantity: 1 };

describe('cartReducer', () => {
    it('adds a product to the cart', () => {
        const state = cartReducer(undefined, { type: 'ADD_CART', payload: mug });

        expect(state.cart).toEqual([mug]);
    });

    it('updates a product already in the cart instead of adding it twice', () => {
        let state = cartReducer(undefined, { type: 'ADD_CART', payload: mug });
        state = cartReducer(state, { type: 'ADD_CART', payload: lamp });
        state = cartReducer(state, { type: 'ADD_CART', payload: { ...mug, quantity: 3 } });

        expect(state.cart).toEqual([{ ...mug, quantity: 3 }, lamp]);
    });

    it('removes a product', () => {
        let state = cartReducer(undefined, { type: 'ADD_CART', payload: mug });
        state = cartReducer(state, { type: 'ADD_CART', payload: lamp });
        state = cartReducer(state, { type: 'REMOVE_CART', payload: mug });

        expect(state.cart).toEqual([lamp]);
    });

    it('empties the cart after an order', () => {
        let state = cartReducer(undefined, { type: 'ADD_CART', payload: mug });
        state = cartReducer(state, { type: 'CLEAR_CART' });

        expect(state).toEqual({ cart: [], totalPrice: 0, cartId: null });
    });
});
