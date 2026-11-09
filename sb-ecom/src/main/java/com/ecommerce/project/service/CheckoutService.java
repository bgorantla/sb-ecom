package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.OrderNotFulfillableException;
import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.Cart;
import com.ecommerce.project.model.CartItem;
import com.ecommerce.project.model.User;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.repositories.AddressRepository;
import com.ecommerce.project.repositories.CartRepository;
import com.ecommerce.project.util.AuthUtil;
import com.ecommerce.project.util.CartTotals;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Checkout with the server in charge of the money:
 *
 * 1. startPayment: works out the amount from the cart in the database and asks Stripe to
 *    start a payment for exactly that. The browser never says how much to charge.
 * 2. The browser collects the card details and pays Stripe directly.
 * 3. completePayment (the browser comes back from Stripe) and handleWebhook (Stripe calls us
 *    directly) both ask Stripe whether the payment really succeeded and for how much, then
 *    turn the cart into an order. Either can arrive first; only one order is ever created.
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final StripeService stripeService;
    private final OrderService orderService;
    private final AuthUtil authUtil;

    public CheckoutService(CartRepository cartRepository, AddressRepository addressRepository,
                           StripeService stripeService, OrderService orderService, AuthUtil authUtil) {
        this.cartRepository = cartRepository;
        this.addressRepository = addressRepository;
        this.stripeService = stripeService;
        this.orderService = orderService;
        this.authUtil = authUtil;
    }

    /** Step 1: returns the client secret the browser needs to show Stripe's payment form. */
    public String startPayment(Long addressId) {
        User user = authUtil.loggedInUser();
        Cart cart = cartRepository.findCartByEmail(user.getEmail());
        if (cart == null || cart.getCartItems().isEmpty()) {
            throw new APIException("Your cart is empty.");
        }

        // The address must be one of this user's own
        Address address = (addressId == null ? null : addressRepository.findById(addressId)
                .filter(a -> a.getUser() != null && user.getEmail().equals(a.getUser().getEmail()))
                .orElse(null));
        if (address == null) {
            throw new APIException("Please choose one of your delivery addresses.");
        }

        // Catch sold-out items now, before the shopper pays
        for (CartItem item : cart.getCartItems()) {
            if (item.getQuantity() > item.getProduct().getQuantity()) {
                throw new APIException("Only " + item.getProduct().getQuantity() + " left of "
                        + item.getProduct().getProductName() + ". Please update your cart.");
            }
        }

        try {
            return stripeService.createPaymentIntent(user, address, CartTotals.inCents(cart)).getClientSecret();
        } catch (StripeException e) {
            log.error("Couldn't start a Stripe payment", e);
            throw new APIException("The payment service isn't responding. Please try again.");
        }
    }

    /** Step 3, browser side: the shopper is back from Stripe with this payment's id. */
    public OrderDTO completePayment(String paymentIntentId) {
        PaymentIntent intent = retrieve(paymentIntentId);
        // Only the person who started this payment may turn it into an order
        if (!authUtil.loggedInEmail().equals(intent.getMetadata().get(StripeService.METADATA_EMAIL))) {
            throw new APIException("Payment not found.");
        }
        return fulfil(intent);
    }

    /** Step 3, Stripe side: called for each event Stripe sends to /api/webhooks/stripe. */
    public void handleWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        Event event = stripeService.parseWebhookEvent(payload, signatureHeader);
        if (!"payment_intent.succeeded".equals(event.getType())) {
            return;   // we only subscribe to this event, but ignore anything else just in case
        }
        // The event carries a copy of the payment; we only take its id and fetch the payment
        // fresh from Stripe below. deserializeUnsafe() reads it even when the event was made with
        // a different Stripe API version than this library's (getObject() wouldn't), which is
        // fine when all we need is the id.
        String paymentIntentId;
        try {
            EventDataObjectDeserializer data = event.getDataObjectDeserializer();
            StripeObject object = data.deserializeUnsafe();
            paymentIntentId = ((PaymentIntent) object).getId();
        } catch (StripeException | RuntimeException e) {
            log.warn("Webhook: couldn't read the payment in event {}", event.getId(), e);
            return;
        }
        try {
            OrderDTO order = fulfil(retrieve(paymentIntentId));
            log.info("Webhook: payment {} is order {}", paymentIntentId, order.getOrderId());
        } catch (APIException e) {
            // Already handled (refunded or not paid): tell Stripe we got it, so it doesn't retry
            log.warn("Webhook: payment {} not turned into an order: {}", paymentIntentId, e.getMessage());
        }
    }

    private OrderDTO fulfil(PaymentIntent intent) {
        if (!"succeeded".equals(intent.getStatus())) {
            throw new APIException("The payment hasn't gone through (status: " + intent.getStatus() + ").");
        }
        String email = intent.getMetadata().get(StripeService.METADATA_EMAIL);
        Long addressId = Long.valueOf(intent.getMetadata().get(StripeService.METADATA_ADDRESS_ID));
        try {
            return orderService.placePaidOrder(email, addressId, intent.getId(), intent.getAmount());
        } catch (OrderNotFulfillableException e) {
            // They paid but we can't deliver: give the money back
            refund(intent.getId());
            throw new APIException(e.getMessage() + " Your payment has been refunded.");
        }
    }

    private PaymentIntent retrieve(String paymentIntentId) {
        if (paymentIntentId == null || !paymentIntentId.startsWith("pi_")) {
            throw new APIException("Payment not found.");
        }
        try {
            return stripeService.retrievePaymentIntent(paymentIntentId);
        } catch (StripeException e) {
            log.warn("Couldn't fetch payment {} from Stripe: {}", paymentIntentId, e.getMessage());
            throw new APIException("Payment not found.");
        }
    }

    private void refund(String paymentIntentId) {
        try {
            stripeService.refund(paymentIntentId);
            log.info("Refunded payment {}", paymentIntentId);
        } catch (StripeException e) {
            // Rare, but a person needs to sort it out in Stripe's dashboard
            log.error("REFUND FAILED for payment {}: refund it in the Stripe dashboard", paymentIntentId, e);
        }
    }
}
