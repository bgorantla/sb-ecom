package com.ecommerce.project.security;

import com.ecommerce.project.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may see and change what. */
class AccessRulesTest extends ApiTestBase {

    private static final String ADDRESS_JSON = """
            {"street": "99 Other Street", "buildingName": "Floor 2", "city": "Dallas",
             "state": "Texas", "country": "USA", "pincode": "75201"}""";

    @Test
    void checkoutNeedsALogin() throws Exception {
        mvc.perform(post("/api/order/stripe-client-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Shopper alice = newShopper();

        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + alice.username + "\", \"password\": \"not-it\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shoppersCannotSeeChangeOrDeleteSomeoneElsesAddress() throws Exception {
        Shopper alice = newShopper();
        Shopper mallory = newShopper();
        long alicesAddress = alice.addAddress();

        mallory.get("/api/addresses/" + alicesAddress).andExpect(status().isNotFound());
        mallory.put("/api/addresses/" + alicesAddress, ADDRESS_JSON).andExpect(status().isNotFound());
        mallory.delete("/api/addresses/" + alicesAddress).andExpect(status().isNotFound());

        // Untouched
        alice.get("/api/addresses/" + alicesAddress)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.street").value("1 Main Street"));
    }

    @Test
    void shoppersListOnlyTheirOwnAddresses() throws Exception {
        Shopper alice = newShopper();
        Shopper bob = newShopper();
        alice.addAddress();
        long bobsAddress = bob.addAddress();

        bob.get("/api/users/addresses")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].addressId").value(bobsAddress));
    }

    @Test
    void onlyAdminsListEveryonesCartsAndAddresses() throws Exception {
        Shopper alice = newShopper();
        Shopper admin = signIn("admin", "adminPass");

        alice.get("/api/carts").andExpect(status().isForbidden());
        alice.get("/api/addresses").andExpect(status().isForbidden());

        admin.get("/api/carts").andExpect(status().isOk());
        admin.get("/api/addresses").andExpect(status().isOk());
    }

    @Test
    void shoppersCannotUseTheAdminApi() throws Exception {
        Shopper alice = newShopper();

        alice.post("/api/admin/categories", "{\"categoryName\": \"Free stuff\"}")
                .andExpect(status().isForbidden());
        alice.get("/api/admin/orders").andExpect(status().isForbidden());
    }
}
