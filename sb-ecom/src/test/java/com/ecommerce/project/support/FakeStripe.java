package com.ecommerce.project.support;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.User;
import com.ecommerce.project.service.StripeService;
import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stands in for Stripe in tests, so they run offline and can decide how each payment turns out.
 *
 * Payments live in a map. A test "pays" with {@link #markSucceeded}, which is what happens at
 * Stripe when a shopper enters a working card. Webhook signatures are checked with Stripe's real
 * code, so the tests prove that forged webhooks are rejected.
 */
public class FakeStripe implements StripeService {

    public static final String WEBHOOK_SECRET = "whsec_test_secret";

    private final Map<String, PaymentIntent> payments = new ConcurrentHashMap<>();
    private final Set<String> refunded = ConcurrentHashMap.newKeySet();

    @Override
    public PaymentIntent createPaymentIntent(User user, Address address, long amountInCents) {
        String id = "pi_test_" + UUID.randomUUID().toString().replace("-", "");
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setAmount(amountInCents);
        intent.setCurrency("usd");
        intent.setStatus("requires_payment_method");
        intent.setClientSecret(id + "_secret_test");
        intent.setMetadata(Map.of(
                METADATA_EMAIL, user.getEmail(),
                METADATA_ADDRESS_ID, String.valueOf(address.getAddressId())));
        payments.put(id, intent);
        return intent;
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        PaymentIntent intent = payments.get(paymentIntentId);
        if (intent == null) {
            throw new InvalidRequestException("No such payment_intent: " + paymentIntentId,
                    "id", null, "resource_missing", 404, null);
        }
        return intent;
    }

    @Override
    public void refund(String paymentIntentId) {
        refunded.add(paymentIntentId);
    }

    @Override
    public Event parseWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, WEBHOOK_SECRET);
    }

    @Override
    public boolean isWebhookConfigured() {
        return true;
    }

    // ---- What tests use to play Stripe's part ----

    /** The shopper paid successfully. */
    public void markSucceeded(String paymentIntentId) {
        payments.get(paymentIntentId).setStatus("succeeded");
    }

    public PaymentIntent payment(String paymentIntentId) {
        return payments.get(paymentIntentId);
    }

    public boolean wasRefunded(String paymentIntentId) {
        return refunded.contains(paymentIntentId);
    }

    /** The JSON Stripe POSTs to the webhook when a payment succeeds. */
    public static String paymentSucceededEvent(String paymentIntentId) {
        return """
                {"id": "evt_%s", "object": "event", "type": "payment_intent.succeeded", "api_version": "%s",
                 "data": {"object": {"id": "%s", "object": "payment_intent"}}}"""
                .formatted(UUID.randomUUID().toString().substring(0, 8), Stripe.API_VERSION, paymentIntentId);
    }

    /**
     * The Stripe-Signature header for a payload: "t=<time>,v1=<HMAC-SHA256 of 'time.payload'>",
     * made with the endpoint's signing secret. Only Stripe and our server know the secret.
     */
    public static String signatureHeader(String payload, String secret) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
