package com.rstltd.skypulse.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.api.dto.ApiResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> apiBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> backfillBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip rate limiting for public endpoints
        if (!path.startsWith("/api/v1/") || path.equals("/api/v1/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        Bucket bucket;
        long limit;
        if (path.startsWith("/api/v1/backfill/")) {
            bucket = backfillBuckets.computeIfAbsent(clientIp, k ->
                    Bucket.builder()
                            .addLimit(Bandwidth.simple(5, Duration.ofHours(1)))
                            .build());
            limit = 5;
        } else {
            bucket = apiBuckets.computeIfAbsent(clientIp, k ->
                    Bucket.builder()
                            .addLimit(Bandwidth.simple(100, Duration.ofMinutes(1)))
                            .build());
            limit = 100;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (!probe.isConsumed()) {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            writeRateLimitResponse(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.error("RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Please try again later."));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
