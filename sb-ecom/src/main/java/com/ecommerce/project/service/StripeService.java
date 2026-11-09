package com.ecommerce.project.service;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.User;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;

/** Everything the app asks Stripe to do. Kept behind an interface so it's easy to fake in tests. */
public interface StripeService {

    /** Metadata saved on each PaymentIntent, so we know whose cart it pays for when it completes. */
    String METADATA_EMAIL = "userEmail";
    String METADATA_ADDRESS_ID = "addressId";

    /** Starts a payment of exactly this amount. The browser finishes it using the client secret. */
    PaymentIntent createPaymentIntent(User user, Address address, long amountInCents) throws StripeException;

    /** Fetches a payment from Stripe, so we can see its real status and amount. */
    PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException;

    /** Gives the shopper their money back, in full. */
    void refund(String paymentIntentId) throws StripeException;

    /** Checks the Stripe-Signature header and returns the event; throws if Stripe didn't send it. */
    Event parseWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException;

    boolean isWebhookConfigured();
}
