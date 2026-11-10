package com.ecommerce.project.demo;

import com.ecommerce.project.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public demo: anyone can sign in as demo-admin and look around, but not change anything. */
@TestPropertySource(properties = "app.demo.enabled=true")
class DemoModeTest extends ApiTestBase {

    @Test
    void theLoginPageCanShowTheDemoAdminsPassword() throws Exception {
        mvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.adminUsername").value("demo-admin"));
    }

    @Test
    void demoAdminCanSeeTheAdminPages() throws Exception {
        Shopper demoAdmin = signIn("demo-admin", "demo-admin");

        demoAdmin.get("/api/admin/orders").andExpect(status().isOk());
        demoAdmin.get("/api/addresses").andExpect(status().isOk());
    }

    @Test
    void demoAdminCannotChangeAnything() throws Exception {
        Shopper demoAdmin = signIn("demo-admin", "demo-admin");

        demoAdmin.post("/api/admin/categories", "{\"categoryName\": \"Vandalism\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.demoReadOnly").value(true));
        demoAdmin.put("/api/admin/orders/1/status", "{\"status\": \"Cancelled\"}")
                .andExpect(status().isForbidden());
        demoAdmin.delete("/api/admin/categories/1")
                .andExpect(status().isForbidden());
    }

    @Test
    void demoAdminCanStillSignOut() throws Exception {
        Shopper demoAdmin = signIn("demo-admin", "demo-admin");

        demoAdmin.post("/api/auth/signout", "").andExpect(status().isOk());
    }

    @Test
    void theStoreOwnersAdminIsNotRestricted() throws Exception {
        Shopper admin = signIn("admin", "adminPass");

        admin.post("/api/admin/categories", "{\"categoryName\": \"Garden " + shortId() + "\"}")
                .andExpect(status().isCreated());
    }
}
