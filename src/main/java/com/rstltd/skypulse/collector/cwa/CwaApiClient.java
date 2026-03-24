package com.rstltd.skypulse.collector.cwa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class CwaApiClient {

    private static final Logger log = LoggerFactory.getLogger(CwaApiClient.class);

    private final WebClient webClient;
    private final String apiKey;

    public CwaApiClient(
            @Qualifier("cwaWebClient") WebClient webClient,
            @Value("${skypulse.cwa.api-key}") String apiKey) {
        this.webClient = webClient;
        this.apiKey = apiKey;
    }

    /**
     * Fetch a CWA dataset by ID.
     * Automatically appends Authorization and format=JSON query parameters.
     */
    public <T> Mono<T> getDataset(String datasetId, Class<T> responseType) {
        log.debug("Fetching CWA dataset: {}", datasetId);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{datasetId}")
                        .queryParam("Authorization", apiKey)
                        .queryParam("format", "JSON")
                        .build(datasetId))
                .retrieve()
                .bodyToMono(responseType);
    }
}
