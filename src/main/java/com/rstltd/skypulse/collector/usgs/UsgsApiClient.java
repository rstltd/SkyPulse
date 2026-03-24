package com.rstltd.skypulse.collector.usgs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
public class UsgsApiClient {

    private static final Logger log = LoggerFactory.getLogger(UsgsApiClient.class);

    private final WebClient webClient;

    public UsgsApiClient(@Qualifier("usgsWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public <T> Mono<T> queryEarthquakes(Class<T> responseType,
                                         double minLat, double maxLat,
                                         double minLon, double maxLon,
                                         double minMag) {
        log.debug("Fetching USGS earthquakes: mag>={}, bbox=[{},{},{},{}]",
                minMag, minLat, maxLat, minLon, maxLon);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fdsnws/event/1/query")
                        .queryParam("format", "geojson")
                        .queryParam("minlatitude", minLat)
                        .queryParam("maxlatitude", maxLat)
                        .queryParam("minlongitude", minLon)
                        .queryParam("maxlongitude", maxLon)
                        .queryParam("minmagnitude", minMag)
                        .build())
                .retrieve()
                .bodyToMono(responseType);
    }

    /**
     * Query earthquakes with date range for historical backfill.
     */
    public <T> Mono<T> queryEarthquakes(Class<T> responseType,
                                         double minLat, double maxLat,
                                         double minLon, double maxLon,
                                         double minMag,
                                         LocalDate startTime, LocalDate endTime) {
        log.debug("Fetching USGS earthquakes: mag>={}, period={} to {}", minMag, startTime, endTime);
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/fdsnws/event/1/query")
                        .queryParam("format", "geojson")
                        .queryParam("minlatitude", minLat)
                        .queryParam("maxlatitude", maxLat)
                        .queryParam("minlongitude", minLon)
                        .queryParam("maxlongitude", maxLon)
                        .queryParam("minmagnitude", minMag)
                        .queryParam("starttime", startTime.toString())
                        .queryParam("endtime", endTime.toString())
                        .build())
                .retrieve()
                .bodyToMono(responseType);
    }
}
