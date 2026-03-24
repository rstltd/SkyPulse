package com.rstltd.skypulse.collector.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CollectorBaseTest {

    @Test
    void execute_happyPath_returnsSuccess() {
        var collector = new TestCollector(
                Mono.just(List.of("a", "b", "c")),
                item -> true,
                3
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(3, result.fetchedCount());
        assertEquals(3, result.validCount());
        assertEquals(3, result.persistedCount());
        assertTrue(result.isSuccess());
        assertNull(result.errorMessage());
    }

    @Test
    void execute_emptyFetch_returnsEmpty() {
        var collector = new TestCollector(
                Mono.just(List.of()),
                item -> true,
                0
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        assertEquals(0, result.fetchedCount());
        assertFalse(collector.persistCalled);
    }

    @Test
    void execute_nullFetch_returnsEmpty() {
        var collector = new TestCollector(
                Mono.justOrEmpty(null),
                item -> true,
                0
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        assertFalse(collector.persistCalled);
    }

    @Test
    void execute_partialValidation_filtersInvalid() {
        var collector = new TestCollector(
                Mono.just(List.of("valid", "invalid", "valid2")),
                item -> !item.equals("invalid"),
                2
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(3, result.fetchedCount());
        assertEquals(2, result.validCount());
        assertEquals(2, result.persistedCount());
    }

    @Test
    void execute_allValidationFails_returnsEmpty() {
        var collector = new TestCollector(
                Mono.just(List.of("a", "b")),
                item -> false,
                0
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.EMPTY, result.status());
        assertFalse(collector.persistCalled);
    }

    @Test
    void execute_validationThrowsForOneItem_skipsIt() {
        var collector = new TestCollector(
                Mono.just(List.of("ok", "throw", "ok2")),
                item -> {
                    if (item.equals("throw")) throw new RuntimeException("bad item");
                    return true;
                },
                2
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(3, result.fetchedCount());
        assertEquals(2, result.validCount());
    }

    @Test
    void execute_persistThrows_returnsFailure() {
        var collector = new TestCollector(
                Mono.just(List.of("a")),
                item -> true,
                0
        ) {
            @Override
            protected int persist(List<String> data) {
                throw new RuntimeException("DB error");
            }
        };

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.FAILURE, result.status());
        assertEquals("DB error", result.errorMessage());
    }

    @Test
    void execute_fetchThrowsNonRetryable400_returnsFailureImmediately() {
        var fetchCount = new AtomicInteger(0);
        var collector = new TestCollector(
                Mono.defer(() -> {
                    fetchCount.incrementAndGet();
                    return Mono.error(WebClientResponseException.create(
                            400, "Bad Request", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
                }),
                item -> true,
                0
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.FAILURE, result.status());
        assertEquals(1, fetchCount.get(), "Should not retry on 400");
    }

    @Test
    void execute_fetchThrows503ThenSucceeds_retriesAndReturnsSuccess() {
        var fetchCount = new AtomicInteger(0);
        var collector = new TestCollector(
                Mono.defer(() -> {
                    if (fetchCount.incrementAndGet() == 1) {
                        return Mono.error(WebClientResponseException.create(
                                503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
                    }
                    return Mono.just(List.of("data"));
                }),
                item -> true,
                1
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(2, fetchCount.get(), "Should have retried once");
        assertEquals(1, result.persistedCount());
    }

    @Test
    void execute_fetchAlwaysThrows503_failsAfterRetries() {
        var fetchCount = new AtomicInteger(0);
        var collector = new TestCollector(
                Mono.defer(() -> {
                    fetchCount.incrementAndGet();
                    return Mono.error(WebClientResponseException.create(
                            503, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));
                }),
                item -> true,
                0
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.FAILURE, result.status());
        assertEquals(3, fetchCount.get(), "Should attempt 1 + 2 retries = 3 total");
    }

    @Test
    void execute_upsertSkipsDuplicates_persistedCountDiffers() {
        var collector = new TestCollector(
                Mono.just(List.of("a", "b", "c")),
                item -> true,
                2  // simulate UPSERT: 3 valid but only 2 actually inserted
        );

        CollectorResult result = collector.execute();

        assertEquals(CollectorResult.Status.SUCCESS, result.status());
        assertEquals(3, result.validCount());
        assertEquals(2, result.persistedCount());
    }

    // --- Test helper ---

    private static class TestCollector extends CollectorBase<String> {

        private final Mono<List<String>> fetchResult;
        private final java.util.function.Predicate<String> validator;
        private final int persistReturnCount;
        boolean persistCalled = false;

        TestCollector(Mono<List<String>> fetchResult,
                      java.util.function.Predicate<String> validator,
                      int persistReturnCount) {
            this.fetchResult = fetchResult;
            this.validator = validator;
            this.persistReturnCount = persistReturnCount;
        }

        @Override
        protected String getSourceName() {
            return "TEST";
        }

        @Override
        protected Mono<List<String>> fetch() {
            return fetchResult;
        }

        @Override
        protected boolean validate(String item) {
            return validator.test(item);
        }

        @Override
        protected int persist(List<String> data) {
            persistCalled = true;
            return persistReturnCount;
        }
    }
}
