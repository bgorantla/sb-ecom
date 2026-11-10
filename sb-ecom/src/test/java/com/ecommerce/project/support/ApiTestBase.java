package com.ecommerce.project.support;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.CategoryRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests that call the REST API the way the frontend does: the whole app runs
 * (security filters, controllers, services, an in-memory H2 database), and shoppers sign up
 * and sign in for real, carrying the JWT cookie on each request. Only Stripe is faked.
 *
 * All test classes share one running app and database, so every test makes its own
 * shoppers and products with unique names instead of relying on a clean database.
 */
@SpringBootTest
@Import(ApiTestBase.FakeStripeConfig.class)
public abstract class ApiTestBase {

    @TestConfiguration
    static class FakeStripeConfig {
        // @Primary: used wherever the app asks for a StripeService, instead of the real one
        @Bean
        @Primary
        FakeStripe fakeStripe() {
            return new FakeStripe();
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    protected FakeStripe stripe;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${spring.ecom.app.jwtCookieName}")
    private String jwtCookieName;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- Users ----

    /** Signs up a new shopper with a unique name and signs them in. */
    protected Shopper newShopper() throws Exception {
        String username = "shopper_" + shortId();
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s@example.com", "password": "secret123"}"""
                        .formatted(username, username)))
                .andExpect(status().isOk());
        return signIn(username, "secret123");
    }

    protected Shopper signIn(String username, String password) throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "password": "%s"}""".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie(jwtCookieName);
        if (session == null) {
            throw new AssertionError("Signing in didn't set the " + jwtCookieName + " cookie");
        }
        return new Shopper(username, JsonPath.read(result.getResponse().getContentAsString(), "$.email"), session);
    }

    /** A signed-in user. Every request carries their JWT cookie, like the browser does. */
    protected class Shopper {
        public final String username;
        public final String email;
        private final Cookie session;

        Shopper(String username, String email, Cookie session) {
            this.username = username;
            this.email = email;
            this.session = session;
        }

        public ResultActions get(String url) throws Exception {
            return send(MockMvcRequestBuilders.get(url));
        }

        public ResultActions post(String url, String json) throws Exception {
            return send(MockMvcRequestBuilders.post(url)
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }

        public ResultActions put(String url, String json) throws Exception {
            return send(MockMvcRequestBuilders.put(url)
                    .contentType(MediaType.APPLICATION_JSON).content(json));
        }

        public ResultActions delete(String url) throws Exception {
            return send(MockMvcRequestBuilders.delete(url));
        }

        private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
            return mvc.perform(request.cookie(session));
        }

        /** Saves a delivery address and returns its id. */
        public long addAddress() throws Exception {
            String body = post("/api/addresses", """
                    {"street": "1 Main Street", "buildingName": "Suite 100", "city": "Austin",
                     "state": "Texas", "country": "USA", "pincode": "78701"}""")
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(body, "$.addressId")).longValue();
        }

        public void addToCart(Product product, int quantity) throws Exception {
            post("/api/carts/products/" + product.getProductId() + "/quantity/" + quantity, "")
                    .andExpect(status().isCreated());
        }

        /** Checkout step 1. Returns the id of the Stripe payment the server started. */
        public String startPayment(long addressId) throws Exception {
            String clientSecret = startPaymentRequest(addressId)
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            // A client secret is "<payment id>_secret_<random>"
            return clientSecret.substring(0, clientSecret.indexOf("_secret_"));
        }

        public ResultActions startPaymentRequest(long addressId) throws Exception {
            return post("/api/order/stripe-client-secret", "{\"addressId\": " + addressId + "}");
        }

        /** Checkout step 3: back from Stripe, asks the server to turn the payment into an order. */
        public ResultActions confirm(String paymentIntentId) throws Exception {
            return post("/api/order/stripe/confirm", "{\"paymentIntentId\": \"" + paymentIntentId + "\"}");
        }
    }

    // ---- Stripe's side ----

    /** Stripe calling our webhook, correctly signed, to say this payment succeeded. */
    protected ResultActions stripeWebhookSaysPaid(String paymentIntentId) throws Exception {
        String payload = FakeStripe.paymentSucceededEvent(paymentIntentId);
        return sendWebhook(payload, FakeStripe.signatureHeader(payload, FakeStripe.WEBHOOK_SECRET));
    }

    protected ResultActions sendWebhook(String payload, String signatureHeader) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/api/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON).content(payload);
        if (signatureHeader != null) {
            request.header("Stripe-Signature", signatureHeader);
        }
        return mvc.perform(request);
    }

    // ---- Catalog and database checks ----

    protected Product newProduct(String name, int stock, double price) {
        Category category = new Category();
        category.setCategoryName("Category " + shortId());
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setProductName(name + " " + shortId());
        product.setDescription("A product made for tests");
        product.setImage("default.png");
        product.setQuantity(stock);
        product.setPrice(price);
        product.setDiscount(0);
        product.setSpecialPrice(price);
        product.setCategory(category);
        return productRepository.save(product);
    }

    protected int stockOf(Product product) {
        return productRepository.findById(product.getProductId()).orElseThrow().getQuantity();
    }

    /** How many orders were created for this Stripe payment. Must never be more than 1. */
    protected int ordersFor(String paymentIntentId) {
        return jdbc.queryForObject("select count(*) from payments where pg_payment_id = ?", Integer.class, paymentIntentId);
    }

    protected int cartItemsOf(Shopper shopper) {
        return jdbc.queryForObject("""
                select count(*) from cart_items ci
                join carts c on c.cart_id = ci.cart_id
                join users u on u.user_id = c.user_id
                where u.username = ?""", Integer.class, shopper.username);
    }

    protected static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
