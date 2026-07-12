package com.rstltd.skypulse.collector.moenv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.moenv.dto.MoenvReservoirRecord;
import com.rstltd.skypulse.domain.hydrology.Reservoir;
import com.rstltd.skypulse.repository.ReservoirRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds reservoir coordinates (and county) into the reservoir dimension from the MOENV GISEPA_P_27
 * dataset, matched by reservoir name (GISEPA {@code dam} == WRA {@code reservoir_name}). Runs after
 * the WRA reservoir collector has populated the dimension names. Reservoirs with no GISEPA match
 * (mostly weirs/barrages absent from the reservoir dataset) are logged, not guessed — a curated
 * seed can fill those gaps later.
 */
@Component
@DependsOn("wraReservoirCollector")
public class MoenvReservoirCoordSeeder {

    private static final Logger log = LoggerFactory.getLogger(MoenvReservoirCoordSeeder.class);

    private final MoenvApiClient moenvApiClient;
    private final ReservoirRepository reservoirRepo;
    private final ObjectMapper objectMapper;

    @Value("${skypulse.moenv.api-key:}")
    private String apiKey;

    @Value("${skypulse.moenv.reservoir-dataset}")
    private String reservoirDataset;

    @Value("${skypulse.collectors.eager-startup-load:true}")
    private boolean eagerStartupLoad;

    public MoenvReservoirCoordSeeder(MoenvApiClient moenvApiClient,
                                     ReservoirRepository reservoirRepo,
                                     ObjectMapper objectMapper) {
        this.moenvApiClient = moenvApiClient;
        this.reservoirRepo = reservoirRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        if (!eagerStartupLoad) return;
        try {
            seed();
        } catch (Exception e) {
            log.warn("[MOENV_RESERVOIR_COORDS] Initial seed failed: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${skypulse.moenv.schedule.reservoir-coords}")
    public void scheduledSeed() {
        try {
            seed();
        } catch (Exception e) {
            log.warn("[MOENV_RESERVOIR_COORDS] Scheduled seed failed: {}", e.getMessage());
        }
    }

    /** @return number of reservoirs whose coordinates were set. */
    int seed() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[MOENV_RESERVOIR_COORDS] No MOENV_API_KEY configured; skipping coordinate seed");
            return 0;
        }

        Map<String, MoenvReservoirRecord> byDam = fetchByDam();
        if (byDam.isEmpty()) {
            log.warn("[MOENV_RESERVOIR_COORDS] MOENV returned no reservoir records");
            return 0;
        }

        List<Reservoir> reservoirs = reservoirRepo.findAll();
        List<Reservoir> updated = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (Reservoir r : reservoirs) {
            MoenvReservoirRecord m = r.getReservoirName() == null ? null : byDam.get(r.getReservoirName());
            BigDecimal[] latLon = m == null ? null : parseLatLon(m);
            if (latLon == null) {
                unmatched.add(r.getReservoirName());
                continue;
            }
            r.setLatitude(latLon[0]);
            r.setLongitude(latLon[1]);
            if (r.getCounty() == null && m.countyname() != null && !m.countyname().isBlank()) {
                r.setCounty(m.countyname());
            }
            updated.add(r);
        }

        if (!updated.isEmpty()) reservoirRepo.saveAll(updated);
        log.info("[MOENV_RESERVOIR_COORDS] Seeded coordinates for {}/{} reservoirs; unmatched (no GISEPA dam): {}",
                updated.size(), reservoirs.size(), unmatched);
        return updated.size();
    }

    /** Fetch GISEPA_P_27 and index by reservoir (dam) name, first row wins. */
    private Map<String, MoenvReservoirRecord> fetchByDam() {
        String json = moenvApiClient.getDataset(reservoirDataset, apiKey).block();
        Map<String, MoenvReservoirRecord> byDam = new LinkedHashMap<>();
        if (json == null) return byDam;
        try {
            List<MoenvReservoirRecord> records = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, MoenvReservoirRecord.class));
            for (MoenvReservoirRecord r : records) {
                if (r.dam() != null && !r.dam().isBlank()) {
                    byDam.putIfAbsent(r.dam().trim(), r);
                }
            }
        } catch (Exception e) {
            log.error("[MOENV_RESERVOIR_COORDS] Failed to parse GISEPA_P_27: {}", e.getMessage());
        }
        return byDam;
    }

    private BigDecimal[] parseLatLon(MoenvReservoirRecord m) {
        if (m.latitute() == null || m.longitute() == null) return null;
        try {
            BigDecimal lat = new BigDecimal(m.latitute().trim());
            BigDecimal lon = new BigDecimal(m.longitute().trim());
            if (lat.doubleValue() < 21.0 || lat.doubleValue() > 26.5
                    || lon.doubleValue() < 118.0 || lon.doubleValue() > 123.0) {
                return null;
            }
            return new BigDecimal[]{lat, lon};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
