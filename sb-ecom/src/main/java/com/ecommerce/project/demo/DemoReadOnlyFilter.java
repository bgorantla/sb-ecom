package com.ecommerce.project.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Lets the shared demo admin look around without changing anything.
 *
 * It runs in the Spring Security filter chain right after the JWT filter, so by the
 * time it runs we know who the user is. If it's the demo admin and the request would
 * modify data (anything other than GET, HEAD or OPTIONS), it answers 403 and the
 * request never reaches a controller. Signing in and out (/api/auth/**) still works.
 *
 * It's created with "new" in WebSecurityConfig rather than being a Spring bean, because
 * Spring Boot also registers every Filter bean with the servlet container, where it
 * would run before anyone is logged in.
 */
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    static final String MESSAGE = "Changes are turned off for the read-only demo admin. "
            + "Use Try as shopper on the login page to place an order.";

    private final DemoSettings demo;

    public DemoReadOnlyFilter(DemoSettings demo) {
        this.demo = demo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        boolean blocked = auth != null
                && demo.isReadOnlyUser(auth.getName())
                && !READ_ONLY_METHODS.contains(request.getMethod())
                && !request.getRequestURI().startsWith("/api/auth/");

        if (blocked) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            // Same shape as the app's other error responses, plus a flag the frontend looks for
            response.getWriter().write("{\"message\":\"" + MESSAGE + "\",\"status\":false,\"demoReadOnly\":true}");
            return;
        }
        chain.doFilter(request, response);
    }
}
