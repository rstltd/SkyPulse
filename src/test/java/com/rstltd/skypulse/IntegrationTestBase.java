package com.rstltd.skypulse;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests that require a database.
 * Requires test DB running: cd docker && docker compose -f docker-compose.test.yml up -d
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
}
