package com.rstltd.skypulse.backfill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client for NASA OMNIWeb CGI interface.
 * Queries hourly OMNI2 data with Kp, Dst, solar wind speed, density, Bz.
 */
@Component
public class OmniWebClient {

    private static final Logger log = LoggerFactory.getLogger(OmniWebClient.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // OMNIWeb variable IDs
    private static final String VARS = "vars=24&vars=28&vars=14&vars=38&vars=40";
    // 24=SW Speed, 28=SW Density, 14=Bz GSE, 38=Kp*10, 40=Dst

    private final WebClient webClient;

    public OmniWebClient(@Qualifier("omniWebWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Query OMNIWeb for hourly space weather data.
     * Returns parsed records with Kp, Dst, solar wind speed, density, Bz.
     */
    public List<OmniWebRecord> query(LocalDate startDate, LocalDate endDate) {
        String body = String.format(
                "activity=retrieve&res=hour&spacecraft=omni2&start_date=%s&end_date=%s&%s",
                startDate.format(DATE_FMT), endDate.format(DATE_FMT), VARS);

        log.info("[OMNIWEB] Querying {} to {}", startDate, endDate);

        String response = webClient.post()
                .uri("/cgi/nx1.cgi")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(java.time.Duration.ofSeconds(60));

        if (response == null) return Collections.emptyList();
        return parseResponse(response);
    }

    /**
     * Parse OMNIWeb fixed-width text response.
     * Format: YEAR DOY HR  Speed  Density  Bz  Kp  Dst
     */
    List<OmniWebRecord> parseResponse(String response) {
        List<OmniWebRecord> records = new ArrayList<>();
        boolean inData = false;

        for (String line : response.split("\n")) {
            String trimmed = line.trim();

            // Data starts after the header line containing "YEAR DOY HR"
            if (trimmed.startsWith("YEAR")) {
                inData = true;
                continue;
            }
            if (!inData || trimmed.isEmpty() || trimmed.startsWith("<")) {
                continue;
            }

            try {
                String[] parts = trimmed.split("\\s+");
                if (parts.length < 8) continue;

                int year = Integer.parseInt(parts[0]);
                int doy = Integer.parseInt(parts[1]);
                int hour = Integer.parseInt(parts[2]);

                Double windSpeed = parseDouble(parts[3], 9999.);
                Double density = parseDouble(parts[4], 999.9);
                Double bz = parseDouble(parts[5], 999.9);
                Integer kpTenths = parseInt(parts[6], 99);
                Integer dst = parseInt(parts[7], 99999);

                records.add(new OmniWebRecord(year, doy, hour, windSpeed, density, bz, kpTenths, dst));
            } catch (Exception e) {
                // Skip unparseable lines
            }
        }
        return records;
    }

    private Double parseDouble(String value, double fillValue) {
        try {
            double d = Double.parseDouble(value);
            return d >= fillValue ? null : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value, int fillValue) {
        try {
            int i = Integer.parseInt(value);
            return i >= fillValue ? null : i;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record OmniWebRecord(
            int year, int doy, int hour,
            Double windSpeed, Double density, Double bz,
            Integer kpTenths, Integer dst
    ) {}
}
