import { configureStore } from '@reduxjs/toolkit';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/api';
import { authReducer } from '../../store/reducers/authReducer';
import { cartReducer } from '../../store/reducers/cartReducer';
import PaymentConfirmation from './PaymentConfirmation';

// Replace the axios instance, so no request leaves the test and each test decides the answer
vi.mock('../../api/api', () => ({ default: { post: vi.fn() } }));

// Stripe sends the shopper to /order-confirm?payment_intent=...&redirect_status=...
function arriveFromStripe(query) {
    const store = configureStore({ reducer: { auth: authReducer, carts: cartReducer } });
    render(
        <Provider store={store}>
            <MemoryRouter initialEntries={[`/order-confirm?${query}`]}>
                <PaymentConfirmation />
            </MemoryRouter>
        </Provider>
    );
}

describe('PaymentConfirmation', () => {
    beforeEach(() => {
        vi.mocked(api.post).mockReset();
    });

    it('asks the server to confirm the payment, then shows the order', async () => {
        vi.mocked(api.post).mockResolvedValue({ data: { orderId: 42, totalAmount: 59.97 } });

        arriveFromStripe('payment_intent=pi_123&redirect_status=succeeded');

        expect(screen.getByText('Confirming your payment...')).toBeInTheDocument();
        expect(await screen.findByText('Order placed!')).toBeInTheDocument();
        expect(screen.getByText(/Order #42 · \$59\.97/)).toBeInTheDocument();
        expect(api.post).toHaveBeenCalledTimes(1);
        expect(api.post).toHaveBeenCalledWith('/order/stripe/confirm', { paymentIntentId: 'pi_123' });
    });

    it("shows the server's reason when no order could be made", async () => {
        vi.mocked(api.post).mockRejectedValue({
            response: { data: { message: 'Lamp sold out while you were paying. Your payment has been refunded.' } },
        });

        arriveFromStripe('payment_intent=pi_123&redirect_status=succeeded');

        expect(await screen.findByText('No order placed')).toBeInTheDocument();
        expect(screen.getByText(/Your payment has been refunded/)).toBeInTheDocument();
    });

    it("doesn't contact the server when Stripe says the payment failed", () => {
        arriveFromStripe('payment_intent=pi_123&redirect_status=failed');

        expect(screen.getByText('No order placed')).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });

    it('says the bank is still working on it when the payment is processing', () => {
        arriveFromStripe('payment_intent=pi_123&redirect_status=processing');

        expect(screen.getByText('Payment processing')).toBeInTheDocument();
        expect(api.post).not.toHaveBeenCalled();
    });
});
