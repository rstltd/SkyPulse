package com.rstltd.skypulse.collector.common;

import com.rstltd.skypulse.service.CollectorStatusService;
import com.rstltd.skypulse.service.SystemLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;

public abstract class CollectorBase<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RETRIES = 2;

    @Autowired(required = false)
    private CollectorStatusService collectorStatusService;

    @Autowired(required = false)
    private SystemLogService systemLogService;

    protected abstract String getSourceName();

    protected abstract Mono<List<T>> fetch();

    protected abstract boolean validate(T item);

    protected abstract int persist(List<T> data);

    protected CollectorResult execute() {
        long start = System.currentTimeMillis();
        String source = getSourceName();
        try {
            List<T> rawData = fetchWithRetry();
            if (rawData == null || rawData.isEmpty()) {
                log.warn("[{}] No data returned", source);
                return recordAndReturn(CollectorResult.empty(source, elapsed(start)));
            }

            List<T> validData = rawData.stream()
                    .filter(item -> {
                        try {
                            return validate(item);
                        } catch (Exception e) {
                            log.debug("[{}] Validation rejected item: {}", source, e.getMessage());
                            return false;
                        }
                    })
                    .toList();

            if (validData.isEmpty()) {
                log.warn("[{}] All {} records failed validation", source, rawData.size());
                return recordAndReturn(CollectorResult.empty(source, elapsed(start)));
            }

            int persisted = persist(validData);
            long duration = elapsed(start);
            log.info("[{}] Collected {} records (fetched={}, valid={}, {}ms)",
                    source, persisted, rawData.size(), validData.size(), duration);
            return recordAndReturn(
                    CollectorResult.success(source, persisted, rawData.size(), validData.size(), duration));

        } catch (Exception e) {
            long duration = elapsed(start);
            log.error("[{}] Collection failed ({}ms): {}", source, duration, e.getMessage());
            return recordAndReturn(CollectorResult.failure(source, duration, e.getMessage()));
        }
    }

    private CollectorResult recordAndReturn(CollectorResult result) {
        if (collectorStatusService != null) {
            collectorStatusService.recordResult(result);
        }
        if (systemLogService != null) {
            systemLogService.recordCollectorResult(result);
        }
        return result;
    }

    private List<T> fetchWithRetry() {
        int attempt = 0;
        while (true) {
            try {
                return fetch().block(FETCH_TIMEOUT);
            } catch (WebClientResponseException e) {
                if (isRetryable(e) && attempt < MAX_RETRIES) {
                    attempt++;
                    long backoff = (long) Math.pow(2, attempt) * 1000;
                    log.warn("[{}] Retry {}/{} after {}ms: {}",
                            getSourceName(), attempt, MAX_RETRIES, backoff, e.getMessage());
                    sleep(backoff);
                } else {
                    throw e;
                }
            } catch (RuntimeException e) {
                if (isConnectionError(e) && attempt < MAX_RETRIES) {
                    attempt++;
                    long backoff = (long) Math.pow(2, attempt) * 1000;
                    log.warn("[{}] Retry {}/{} after {}ms: {}",
                            getSourceName(), attempt, MAX_RETRIES, backoff, e.getMessage());
                    sleep(backoff);
                } else {
                    throw e;
                }
            }
        }
    }

    private boolean isRetryable(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        return status == 503 || status == 504 || status == 429;
    }

    private boolean isConnectionError(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConnectException
                || (cause != null && cause.getClass().getName().contains("ConnectTimeoutException"));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry backoff", ie);
        }
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
