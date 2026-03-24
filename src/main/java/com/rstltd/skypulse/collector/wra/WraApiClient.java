package com.rstltd.skypulse.collector.wra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WraApiClient {

    private static final Logger log = LoggerFactory.getLogger(WraApiClient.class);

    private final WebClient webClient;

    public WraApiClient(@Qualifier("wraWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getDataset(String guid) {
        log.debug("Fetching WRA dataset: {}", guid);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{guid}")
                        .queryParam("format", "JSON")
                        .build(guid))
                .retrieve()
                .bodyToMono(String.class);
    }
}
