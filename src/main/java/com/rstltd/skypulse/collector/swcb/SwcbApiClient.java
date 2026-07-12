package com.rstltd.skypulse.collector.swcb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for the SWCB (農村發展及水土保持署 / ardswc) debris-flow open data. The underlying
 * WebClient sets a browser User-Agent and follows the .ashx -> /api redirect (see WebClientConfig).
 */
@Component
public class SwcbApiClient {

    private static final Logger log = LoggerFactory.getLogger(SwcbApiClient.class);

    private final WebClient webClient;

    public SwcbApiClient(@Qualifier("swcbWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getRawJson(String path) {
        log.debug("Fetching SWCB: {}", path);
        return webClient.get()
                .uri(path)
                .retrieve()
                .bodyToMono(String.class);
    }
}
