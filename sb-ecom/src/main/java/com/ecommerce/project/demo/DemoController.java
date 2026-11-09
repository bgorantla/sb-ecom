package com.ecommerce.project.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend whether demo mode is on and how to log in as the demo admin.
 * The frontend uses it to show the test-mode banner and the "Try as..." buttons.
 * It's under /api/public, so no login is needed.
 */
@RestController
@RequestMapping("/api/public")
public class DemoController {

    private final DemoSettings demo;

    public DemoController(DemoSettings demo) {
        this.demo = demo;
    }

    @GetMapping("/demo")
    public DemoInfo demoInfo() {
        if (!demo.isEnabled()) {
            return new DemoInfo(false, null, null);
        }
        return new DemoInfo(true, demo.getAdminUsername(), demo.getAdminPassword());
    }

    public record DemoInfo(boolean enabled, String adminUsername, String adminPassword) {
    }
}
