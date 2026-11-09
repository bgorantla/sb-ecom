package com.ecommerce.project.service;

import com.ecommerce.project.model.Address;
import com.ecommerce.project.model.User;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.CustomerSearchResult;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerSearchParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeServiceImpl implements StripeService {

    @Value("${stripe.secret.key}")
    private String stripeApiKey;

    // Signing secret of the webhook endpoint (whsec_...), from Stripe's dashboard
    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    // Empty means the real Stripe API. Set it only to test against a fake Stripe.
    @Value("${stripe.api-base:}")
    private String apiBase;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
        if (!apiBase.isBlank()) {
            Stripe.overrideApiBase(apiBase);
        }
    }

    @Override
    public PaymentIntent createPaymentIntent(User user, Address address, long amountInCents) throws StripeException {
        Customer customer = findOrCreateCustomer(user, address);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setCustomer(customer.getId())
                .setDescription("E-Shop order for " + user.getEmail())
                .putMetadata(METADATA_EMAIL, user.getEmail())
                .putMetadata(METADATA_ADDRESS_ID, String.valueOf(address.getAddressId()))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        return PaymentIntent.create(params);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    @Override
    public void refund(String paymentIntentId) throws StripeException {
        Refund.create(RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build());
    }

    @Override
    public Event parseWebhookEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
    }

    @Override
    public boolean isWebhookConfigured() {
        return !webhookSecret.isBlank();
    }

    // Reuses the Stripe customer with this email, so repeat buyers show up once in Stripe's dashboard
    private Customer findOrCreateCustomer(User user, Address address) throws StripeException {
        String email = user.getEmail().replace("'", "\\'");
        CustomerSearchResult found = Customer.search(
                CustomerSearchParams.builder().setQuery("email:'" + email + "'").build());
        if (!found.getData().isEmpty()) {
            return found.getData().get(0);
        }

        return Customer.create(CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getUserName())
                .setAddress(CustomerCreateParams.Address.builder()
                        .setLine1(address.getStreet())
                        .setLine2(address.getBuildingName())
                        .setCity(address.getCity())
                        .setState(address.getState())
                        .setPostalCode(address.getPincode())
                        .setCountry(address.getCountry())
                        .build())
                .build());
    }
}
