package com.ecommerce.project.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Settings for the public demo (the app.demo.* properties).
 *
 * When demo mode is on, the app creates a shared "demo admin" account whose
 * password is shown on the login page. That account can see the whole admin
 * panel but can't change anything (see DemoReadOnlyFilter).
 */
@Component
public class DemoSettings {

    private final boolean enabled;
    private final String adminUsername;
    private final String adminPassword;

    public DemoSettings(@Value("${app.demo.enabled}") boolean enabled,
                        @Value("${app.demo.admin-username}") String adminUsername,
                        @Value("${app.demo.admin-password}") String adminPassword) {
        this.enabled = enabled;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    /** True for the shared demo admin, which may look at everything but change nothing. */
    public boolean isReadOnlyUser(String username) {
        return enabled && adminUsername.equals(username);
    }
}
