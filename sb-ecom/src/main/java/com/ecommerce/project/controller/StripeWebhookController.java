package com.ecommerce.project.controller;

import com.ecommerce.project.service.CheckoutService;
import com.ecommerce.project.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe calls this when a payment succeeds, even if the shopper closed the tab before
 * coming back to the site. Nobody is logged in here; instead every request must carry a
 * valid Stripe-Signature header, made with the webhook's signing secret.
 */
@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private final CheckoutService checkoutService;
    private final StripeService stripeService;

    public StripeWebhookController(CheckoutService checkoutService, StripeService stripeService) {
        this.checkoutService = checkoutService;
        this.stripeService = stripeService;
    }

    // The raw body (a String, not parsed JSON) is needed: the signature covers the exact bytes
    @PostMapping("/stripe")
    public ResponseEntity<String> stripeEvent(@RequestBody String payload,
                                              @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (!stripeService.isWebhookConfigured()) {
            return new ResponseEntity<>("Webhook signing secret not configured", HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (signature == null) {
            return new ResponseEntity<>("Missing Stripe-Signature header", HttpStatus.BAD_REQUEST);
        }
        try {
            checkoutService.handleWebhook(payload, signature);
            return ResponseEntity.ok("ok");
        } catch (SignatureVerificationException e) {
            return new ResponseEntity<>("Invalid signature", HttpStatus.BAD_REQUEST);
        }
    }
}
