package com.rstltd.skypulse.collector.wra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.wra.dto.WraStationInfoRecord;
import com.rstltd.skypulse.collector.wra.dto.WraWaterLevelRecord;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.domain.station.WaterLevelStation;
import com.rstltd.skypulse.repository.WaterLevelObservationRepository;
import com.rstltd.skypulse.repository.WaterLevelStationRepository;
import com.rstltd.skypulse.service.StationRegistry;
import com.rstltd.skypulse.util.TimeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class WraWaterLevelCollector extends CollectorBase<WraWaterLevelRecord> {

    private final WraApiClient wraApiClient;
    private final WaterLevelObservationRepository waterLevelRepo;
    private final StationRegistry stationRegistry;
    private final WaterLevelStationRepository waterLevelStationRepo;
    private final ObjectMapper objectMapper;

    private static final Pattern COUNTY_PATTERN =
            Pattern.compile("^(.{2,3}[市縣])");
    private static final Pattern TOWNSHIP_PATTERN =
            Pattern.compile("^.{2,3}[市縣](.{2,3}[區鄉鎮市])");

    @Value("${skypulse.wra.water-level-guid}")
    private String waterLevelGuid;

    @Value("${skypulse.wra.station-info-guid}")
    private String stationInfoGuid;

    public WraWaterLevelCollector(WraApiClient wraApiClient,
                                  WaterLevelObservationRepository waterLevelRepo,
                                  StationRegistry stationRegistry,
                                  WaterLevelStationRepository waterLevelStationRepo,
                                  ObjectMapper objectMapper) {
        this.wraApiClient = wraApiClient;
        this.waterLevelRepo = waterLevelRepo;
        this.stationRegistry = stationRegistry;
        this.waterLevelStationRepo = waterLevelStationRepo;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        try {
            loadStationInfo();
        } catch (Exception e) {
            log.warn("[WRA_WATER_LEVEL] Failed to load station info at startup: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "${skypulse.wra.schedule.water-level}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "WRA_WATER_LEVEL";
    }

    @Override
    protected Mono<List<WraWaterLevelRecord>> fetch() {
        return wraApiClient.getDataset(waterLevelGuid)
                .map(json -> {
                    try {
                        return objectMapper.readValue(json,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, WraWaterLevelRecord.class));
                    } catch (JsonProcessingException e) {
                        log.error("[WRA_WATER_LEVEL] Failed to parse response: {}", e.getMessage());
                        return Collections.<WraWaterLevelRecord>emptyList();
                    }
                });
    }

    @Override
    protected boolean validate(WraWaterLevelRecord item) {
        return item.stationid() != null && !item.stationid().isBlank()
                && item.datetime() != null && !item.datetime().isBlank()
                && parseSafe(item.waterlevel()) != null;
    }

    @Override
    protected int persist(List<WraWaterLevelRecord> data) {
        if (data.isEmpty()) return 0;

        OffsetDateTime batchTime = TimeUtils.toUtcOffset(
                TimeUtils.parseWraTimestamp(data.get(0).datetime()));
        Set<String> existingKeys = waterLevelRepo
                .findByTimeBetween(batchTime.minusMinutes(30), batchTime.plusMinutes(30))
                .stream()
                .map(r -> r.getTime() + "|" + r.getStationCode())
                .collect(Collectors.toSet());

        List<WaterLevelObservation> newObs = data.stream()
                .map(this::mapToEntity)
                .filter(obs -> !existingKeys.contains(obs.getTime() + "|" + obs.getStationCode()))
                .toList();

        if (!newObs.isEmpty()) {
            waterLevelRepo.saveAll(newObs);
            waterLevelRepo.flush();
        }
        return newObs.size();
    }

    private void loadStationInfo() throws JsonProcessingException {
        String json = wraApiClient.getDataset(stationInfoGuid).block();
        List<WraStationInfoRecord> records = objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(
                        List.class, WraStationInfoRecord.class));

        int count = 0;
        for (WraStationInfoRecord r : records) {
            if (r.basinidentifier() == null || r.basinidentifier().isBlank()) continue;
            if (!"現存".equals(r.observationstatus())) continue;

            try {
                final String code = r.basinidentifier();
                String county = null, township = null;
                if (r.locationaddress() != null) {
                    county = parseCounty(r.locationaddress());
                    township = parseTownship(r.locationaddress());
                }
                // Coordinates are added in step 3 (WRA locationbytwd97_xy -> WGS84 via proj4j).
                stationRegistry.register(code,
                        r.observatoryname() != null ? r.observatoryname() : code, "WRA",
                        null, null, null, county, township, "WATER_LEVEL", stationInfoGuid);

                WaterLevelStation wls = waterLevelStationRepo.findById(code).orElseGet(() -> {
                    WaterLevelStation w = new WaterLevelStation();
                    w.setStationCode(code);
                    return w;
                });
                wls.setRiverName(r.rivername());
                wls.setAlertLevel1(parseSafe(r.alertlevel1()));
                wls.setAlertLevel2(parseSafe(r.alertlevel2()));
                wls.setAlertLevel3(parseSafe(r.alertlevel3()));
                waterLevelStationRepo.save(wls);
                count++;
            } catch (Exception e) {
                log.debug("[WRA_WATER_LEVEL] Failed to process station {}: {}",
                        r.basinidentifier(), e.getMessage());
            }
        }
        log.info("[WRA_WATER_LEVEL] Registered/updated {} water-level stations", count);
    }

    private WaterLevelObservation mapToEntity(WraWaterLevelRecord record) {
        WaterLevelObservation obs = new WaterLevelObservation();
        obs.setTime(TimeUtils.toUtcOffset(TimeUtils.parseWraTimestamp(record.datetime())));
        obs.setStationCode(record.stationid());
        obs.setWaterLevel(parseSafe(record.waterlevel()));
        obs.setSource("WRA");
        try {
            obs.setRawData(objectMapper.writeValueAsString(record));
        } catch (JsonProcessingException ignored) {}
        return obs;
    }

    static String parseCounty(String address) {
        if (address == null) return null;
        Matcher m = COUNTY_PATTERN.matcher(normalizeAddress(address));
        return m.find() ? normalizeCounty(m.group(1)) : null;
    }

    static String parseTownship(String address) {
        if (address == null) return null;
        Matcher m = TOWNSHIP_PATTERN.matcher(normalizeAddress(address));
        return m.find() ? normalizeTownship(m.group(1)) : null;
    }

    /** Normalize address: remove extra whitespace, fix common data issues */
    static String normalizeAddress(String address) {
        if (address == null) return null;
        // Remove extra whitespace within text (e.g., "潭子 區" → "潭子區")
        return address.replaceAll("\\s+", "");
    }

    /** Normalize county: 台→臺, fix typos, merge deprecated counties */
    static String normalizeCounty(String county) {
        if (county == null) return null;
        // 台 → 臺 (official standard)
        county = county.replace("台", "臺");
        // Fix known typos
        county = county.replace("苗粟", "苗栗");
        // Merge deprecated county-level cities into current counties
        if ("屏東市".equals(county)) return "屏東縣";
        if ("臺中縣".equals(county)) return "臺中市";
        return county;
    }

    /** Normalize township: remove duplicated suffix */
    static String normalizeTownship(String township) {
        if (township == null) return null;
        // Fix duplicated suffix (e.g., "南投市市" → "南投市")
        township = township.replaceAll("(市)市$", "$1");
        township = township.replaceAll("(區)區$", "$1");
        township = township.replaceAll("(鄉)鄉$", "$1");
        township = township.replaceAll("(鎮)鎮$", "$1");
        return township;
    }

    private BigDecimal parseSafe(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
