package com.rstltd.skypulse.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class SessionOrApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final String apiKey;
    private final String adminKey;

    public SessionOrApiKeyAuthFilter(String apiKey, String adminKey) {
        this.apiKey = apiKey;
        this.adminKey = adminKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 1. Check existing session authentication
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated()
                && !(existingAuth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Check session for stored security context
        HttpSession session = request.getSession(false);
        if (session != null) {
            SecurityContext storedContext = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
            if (storedContext != null && storedContext.getAuthentication() != null
                    && storedContext.getAuthentication().isAuthenticated()) {
                SecurityContextHolder.setContext(storedContext);
                filterChain.doFilter(request, response);
                return;
            }
        }

        // 3. Fall back to API Key authentication
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey != null && !providedKey.isBlank()) {
            if (adminKey != null && !adminKey.isBlank() && adminKey.equals(providedKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "admin", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else if (apiKey != null && !apiKey.isBlank() && apiKey.equals(providedKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "user", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
