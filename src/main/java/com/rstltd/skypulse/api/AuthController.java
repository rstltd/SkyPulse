package com.rstltd.skypulse.api;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.LoginRequest;
import com.rstltd.skypulse.api.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Auth", description = "Authentication and session management")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${skypulse.security.login.username:admin}")
    private String loginUsername;

    @Value("${skypulse.security.login.password:}")
    private String loginPassword;

    @Operation(summary = "Login with username and password")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        if (loginPassword.isBlank()) {
            return ApiResponse.error("Login is not configured");
        }

        if (!loginUsername.equals(request.username()) || !loginPassword.equals(request.password())) {
            return ApiResponse.error("Invalid username or password");
        }

        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_USER"));
        var auth = new UsernamePasswordAuthenticationToken(loginUsername, null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        return ApiResponse.ok(new LoginResponse(loginUsername, "ADMIN"));
    }

    @Operation(summary = "Logout and invalidate session")
    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok("Logged out");
    }

    @Operation(summary = "Get current authenticated user info")
    @GetMapping("/me")
    public ApiResponse<LoginResponse> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ApiResponse.error("Not authenticated");
        }

        SecurityContext context = (SecurityContext) session.getAttribute("SPRING_SECURITY_CONTEXT");
        if (context == null || context.getAuthentication() == null
                || !context.getAuthentication().isAuthenticated()) {
            return ApiResponse.error("Not authenticated");
        }

        var auth = context.getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return ApiResponse.ok(new LoginResponse(
                auth.getName(),
                isAdmin ? "ADMIN" : "USER"));
    }
}
