package com.rstltd.skypulse.collector.moenv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Client for the Ministry of Environment (moenv) open data API. */
@Component
public class MoenvApiClient {

    private static final Logger log = LoggerFactory.getLogger(MoenvApiClient.class);

    private final WebClient webClient;

    public MoenvApiClient(@Qualifier("moenvWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getDataset(String datasetId, String apiKey) {
        log.debug("Fetching MOENV dataset: {}", datasetId);
        return webClient.get()
                .uri(b -> b.path("/{id}")
                        .queryParam("api_key", apiKey)
                        .queryParam("format", "JSON")
                        .queryParam("limit", 1000)
                        .build(datasetId))
                .retrieve()
                .bodyToMono(String.class);
    }
}
