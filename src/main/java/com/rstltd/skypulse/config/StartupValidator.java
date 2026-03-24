package com.rstltd.skypulse.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    @Value("${skypulse.cwa.api-key:}")
    private String cwaApiKey;

    @Value("${skypulse.security.api-key:}")
    private String apiKey;

    @Value("${skypulse.security.admin-key:}")
    private String adminKey;

    private final Environment environment;

    public StartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (isBlank(cwaApiKey)) {
            logIssue(isProd, "CWA_API_KEY not set — CWA collectors will fail");
        }

        if (isBlank(apiKey)) {
            logIssue(isProd, "SKYPULSE_API_KEY not set — API endpoints are unprotected");
        }

        if (isBlank(adminKey)) {
            logIssue(isProd, "SKYPULSE_ADMIN_KEY not set — admin endpoints are unprotected");
        }

        if (apiKey != null && adminKey != null && apiKey.equals(adminKey) && !isBlank(apiKey)) {
            log.warn("[STARTUP] API key and admin key are identical — use different keys for proper access control");
        }

        log.info("[STARTUP] Configuration validation complete. Active profiles: {}",
                Arrays.toString(environment.getActiveProfiles()));
    }

    private void logIssue(boolean isProd, String message) {
        if (isProd) {
            log.error("[STARTUP] PRODUCTION: {}", message);
        } else {
            log.warn("[STARTUP] {}", message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
