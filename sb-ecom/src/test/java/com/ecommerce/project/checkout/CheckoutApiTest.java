package com.ecommerce.project.checkout;

import com.ecommerce.project.model.Product;
import com.ecommerce.project.support.ApiTestBase;
import com.ecommerce.project.support.FakeStripe;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The checkout from start to finish: start a payment, pay at (fake) Stripe, then confirm from
 * the browser and/or Stripe's webhook. Each test is one way checkout could go wrong.
 */
class CheckoutApiTest extends ApiTestBase {

    @Test
    void chargesTheCartTotalFromTheDatabase_notAnAmountFromTheBrowser() throws Exception {
        Shopper alice = newShopper();
        alice.addToCart(newProduct("Mug", 10, 19.99), 3);
        alice.addToCart(newProduct("Lamp", 10, 45.50), 1);

        // The old endpoint took the amount from the request. A request can still try:
        String clientSecret = alice.post("/api/order/stripe-client-secret",
                        "{\"addressId\": " + alice.addAddress() + ", \"amount\": 1}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String paymentId = clientSecret.substring(0, clientSecret.indexOf("_secret_"));
        assertThat(stripe.payment(paymentId).getAmount()).isEqualTo(10_547L);   // 3 × $19.99 + $45.50
    }

    @Test
    void aPaidPaymentBecomesOneOrder_andReportingItAgainDoesNothing() throws Exception {
        Shopper alice = newShopper();
        Product mug = newProduct("Mug", 10, 19.99);
        alice.addToCart(mug, 3);
        String paymentId = alice.startPayment(alice.addAddress());
        stripe.markSucceeded(paymentId);

        String order = alice.confirm(paymentId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(59.97))
                .andExpect(jsonPath("$.orderStatus").value("Accepted"))
                .andReturn().getResponse().getContentAsString();
        int orderId = JsonPath.read(order, "$.orderId");

        assertThat(ordersFor(paymentId)).isEqualTo(1);
        assertThat(stockOf(mug)).isEqualTo(7);
        assertThat(cartItemsOf(alice)).isZero();

        // Refreshing the confirmation page, and Stripe's webhook arriving later, change nothing
        alice.confirm(paymentId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId));
        stripeWebhookSaysPaid(paymentId).andExpect(status().isOk());

        assertThat(ordersFor(paymentId)).isEqualTo(1);
        assertThat(stockOf(mug)).isEqualTo(7);
    }

    @Test
    void cannotConfirmAPaymentThatHasNotGoneThrough() throws Exception {
        Shopper alice = newShopper();
        alice.addToCart(newProduct("Mug", 10, 19.99), 1);
        String paymentId = alice.startPayment(alice.addAddress());

        alice.confirm(paymentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("hasn't gone through")));
        assertThat(ordersFor(paymentId)).isZero();
    }

    @Test
    void cannotClaimSomeoneElsesPayment() throws Exception {
        Shopper alice = newShopper();
        Shopper mallory = newShopper();
        alice.addToCart(newProduct("Mug", 10, 19.99), 1);
        String paymentId = alice.startPayment(alice.addAddress());
        stripe.markSucceeded(paymentId);

        mallory.confirm(paymentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Payment not found."));
        assertThat(ordersFor(paymentId)).isZero();
    }

    @Test
    void theWebhookAloneCreatesTheOrder_whenTheShopperClosesTheTab() throws Exception {
        Shopper alice = newShopper();
        alice.addToCart(newProduct("Mug", 10, 19.99), 1);
        String paymentId = alice.startPayment(alice.addAddress());
        stripe.markSucceeded(paymentId);

        stripeWebhookSaysPaid(paymentId).andExpect(status().isOk());

        assertThat(ordersFor(paymentId)).isEqualTo(1);
        assertThat(cartItemsOf(alice)).isZero();
    }

    @Test
    void webhooksWithoutStripesSignatureAreRejected() throws Exception {
        Shopper alice = newShopper();
        alice.addToCart(newProduct("Mug", 10, 19.99), 1);
        String paymentId = alice.startPayment(alice.addAddress());
        stripe.markSucceeded(paymentId);
        String payload = FakeStripe.paymentSucceededEvent(paymentId);

        sendWebhook(payload, FakeStripe.signatureHeader(payload, "whsec_guessed_secret"))
                .andExpect(status().isBadRequest());
        sendWebhook(payload, null)
                .andExpect(status().isBadRequest());
        // Signed for a different body, e.g. someone replaying a real signature with a changed payment id
        sendWebhook(payload, FakeStripe.signatureHeader(FakeStripe.paymentSucceededEvent("pi_other"), FakeStripe.WEBHOOK_SECRET))
                .andExpect(status().isBadRequest());

        assertThat(ordersFor(paymentId)).isZero();
    }

    @Test
    void whenTwoPeoplePayForTheLastOne_theSecondIsRefunded() throws Exception {
        Product lastBoard = newProduct("Chopping Board", 1, 25.00);
        Shopper dave = newShopper();
        Shopper erin = newShopper();
        dave.addToCart(lastBoard, 1);
        erin.addToCart(lastBoard, 1);
        String davesPayment = dave.startPayment(dave.addAddress());
        String erinsPayment = erin.startPayment(erin.addAddress());
        stripe.markSucceeded(davesPayment);
        stripe.markSucceeded(erinsPayment);

        dave.confirm(davesPayment).andExpect(status().isCreated());
        erin.confirm(erinsPayment)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("sold out")))
                .andExpect(jsonPath("$.message", containsString("refunded")));

        assertThat(stockOf(lastBoard)).isZero();
        assertThat(ordersFor(erinsPayment)).isZero();
        assertThat(stripe.wasRefunded(erinsPayment)).isTrue();
        assertThat(stripe.wasRefunded(davesPayment)).isFalse();
    }

    @Test
    void ifTheCartChangesWhilePaying_thePaymentIsRefunded() throws Exception {
        Shopper fay = newShopper();
        fay.addToCart(newProduct("Mug", 10, 19.99), 1);
        String paymentId = fay.startPayment(fay.addAddress());
        fay.addToCart(newProduct("Lamp", 10, 45.50), 1);   // in another tab, after paying started
        stripe.markSucceeded(paymentId);

        fay.confirm(paymentId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("changed")));

        assertThat(ordersFor(paymentId)).isZero();
        assertThat(stripe.wasRefunded(paymentId)).isTrue();
    }

    @Test
    void cannotPayWithSomeoneElsesAddress() throws Exception {
        Shopper alice = newShopper();
        Shopper mallory = newShopper();
        long alicesAddress = alice.addAddress();
        mallory.addToCart(newProduct("Mug", 10, 19.99), 1);

        mallory.startPaymentRequest(alicesAddress)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("your delivery addresses")));
    }

    @Test
    void cannotPayForAnEmptyCart() throws Exception {
        Shopper alice = newShopper();

        alice.startPaymentRequest(alice.addAddress())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Your cart is empty."));
    }

    @Test
    void cannotPayForMoreThanIsInStock() throws Exception {
        Shopper alice = newShopper();
        Product mug = newProduct("Mug", 5, 19.99);
        alice.addToCart(mug, 3);
        // Meanwhile, other shoppers buy 4
        jdbc.update("update products set quantity = 1 where product_id = ?", mug.getProductId());

        alice.startPaymentRequest(alice.addAddress())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only 1 left")));
    }

    /**
     * The browser and Stripe's webhook often report the same payment within milliseconds.
     * Fire several of each at the same moment: still exactly one order, stock taken once.
     */
    @Test
    void simultaneousReportsOfTheSamePayment_createExactlyOneOrder() throws Exception {
        Shopper carol = newShopper();
        Product lamp = newProduct("Lamp", 10, 45.50);
        carol.addToCart(lamp, 2);
        String paymentId = carol.startPayment(carol.addAddress());
        stripe.markSucceeded(paymentId);

        int reports = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(reports);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < reports; i++) {
            boolean fromBrowser = i % 2 == 0;
            results.add(pool.submit(() -> {
                go.await();
                if (fromBrowser) {
                    String order = carol.confirm(paymentId).andExpect(status().isCreated())
                            .andReturn().getResponse().getContentAsString();
                    return "order " + JsonPath.read(order, "$.orderId");
                }
                stripeWebhookSaysPaid(paymentId).andExpect(status().isOk());
                return "webhook ok";
            }));
        }
        go.countDown();
        Set<String> answers = new HashSet<>();
        for (Future<String> result : results) {
            answers.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(ordersFor(paymentId)).isEqualTo(1);
        assertThat(stockOf(lamp)).isEqualTo(8);
        // Every browser request got the same order back
        assertThat(answers).hasSize(2).contains("webhook ok");
    }
}
