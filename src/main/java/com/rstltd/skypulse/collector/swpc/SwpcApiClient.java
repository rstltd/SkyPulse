package com.rstltd.skypulse.collector.swpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SwpcApiClient {

    private static final Logger log = LoggerFactory.getLogger(SwpcApiClient.class);

    private final WebClient webClient;

    public SwpcApiClient(@Qualifier("swpcWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Fetch raw JSON string from SWPC endpoint.
     * Used for endpoints that return non-standard formats (e.g. 2D arrays).
     */
    public Mono<String> getRawJson(String path) {
        log.debug("Fetching SWPC raw: {}", path);
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * Fetch and deserialize a typed response from SWPC endpoint.
     */
    public <T> Mono<T> get(String path, Class<T> responseType) {
        log.debug("Fetching SWPC: {}", path);
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(responseType);
    }
}
