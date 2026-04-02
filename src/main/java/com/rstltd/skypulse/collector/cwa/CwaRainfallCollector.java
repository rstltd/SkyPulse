package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaRainfallResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.repository.StationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CwaRainfallCollector extends CollectorBase<CwaRainfallResponse.Station> {

    private final CwaApiClient cwaApiClient;
    private final RainfallObservationRepository rainfallRepo;
    private final StationRepository stationRepo;
    private final ObjectMapper objectMapper;

    public CwaRainfallCollector(CwaApiClient cwaApiClient,
                                RainfallObservationRepository rainfallRepo,
                                StationRepository stationRepo,
                                ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.rainfallRepo = rainfallRepo;
        this.stationRepo = stationRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.rainfall}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_RAINFALL";
    }

    @Override
    protected Mono<List<CwaRainfallResponse.Station>> fetch() {
        return cwaApiClient.getDataset("O-A0002-001", CwaRainfallResponse.class)
                .map(response -> {
                    if (response.records() != null && response.records().Station() != null) {
                        return response.records().Station();
                    }
                    return Collections.<CwaRainfallResponse.Station>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaRainfallResponse.Station item) {
        if (item.StationId() == null || item.ObsTime() == null
                || item.ObsTime().DateTime() == null || item.RainfallElement() == null) {
            return false;
        }
        var now = item.RainfallElement().Now();
        if (now == null || now.Precipitation() == null) {
            return false;
        }
        try {
            double precip = Double.parseDouble(now.Precipitation());
            return precip >= 0; // CWA uses negative values like -998 for no data
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    protected int persist(List<CwaRainfallResponse.Station> data) {
        if (data.isEmpty()) return 0;

        // Find existing keys to avoid duplicates
        OffsetDateTime batchTime = TimeUtils.toUtcOffset(
                TimeUtils.parseIsoOffset(data.get(0).ObsTime().DateTime()));
        Set<String> existingKeys = rainfallRepo
                .findByTimeBetween(batchTime.minusMinutes(1), batchTime.plusMinutes(1))
                .stream()
                .map(r -> r.getTime() + "|" + r.getStationCode())
                .collect(Collectors.toSet());

        List<RainfallObservation> newObs = data.stream()
                .map(this::mapToEntity)
                .filter(obs -> !existingKeys.contains(obs.getTime() + "|" + obs.getStationCode()))
                .toList();

        // Auto-register stations
        data.forEach(this::ensureStation);

        if (!newObs.isEmpty()) {
            rainfallRepo.saveAll(newObs);
            rainfallRepo.flush();
        }
        return newObs.size();
    }

    private RainfallObservation mapToEntity(CwaRainfallResponse.Station station) {
        RainfallObservation obs = new RainfallObservation();
        obs.setTime(TimeUtils.toUtcOffset(
                TimeUtils.parseIsoOffset(station.ObsTime().DateTime())));
        obs.setStationCode(station.StationId());
        obs.setPrecipitation(new BigDecimal(station.RainfallElement().Now().Precipitation()));
        obs.setSource("CWA");
        try {
            obs.setRawData(objectMapper.writeValueAsString(station));
        } catch (JsonProcessingException e) {
            log.warn("[CWA_RAINFALL] Failed to serialize raw data for station {}", station.StationId());
        }
        return obs;
    }

    private void ensureStation(CwaRainfallResponse.Station station) {
        if (stationRepo.existsByStationCode(station.StationId())) {
            return;
        }
        try {
            var entity = new com.rstltd.skypulse.domain.station.Station();
            entity.setStationCode(station.StationId());
            entity.setStationName(station.StationName());
            entity.setSource("CWA");
            entity.setStationType("RAINFALL");
            entity.setIsActive(true);

            if (station.GeoInfo() != null) {
                entity.setCounty(station.GeoInfo().CountyName());
                entity.setTownship(station.GeoInfo().TownName());
                if (station.GeoInfo().StationAltitude() != null) {
                    entity.setAltitude(new BigDecimal(station.GeoInfo().StationAltitude()));
                }
                // Use WGS84 coordinates
                if (station.GeoInfo().Coordinates() != null) {
                    station.GeoInfo().Coordinates().stream()
                            .filter(c -> "WGS84".equals(c.CoordinateName()))
                            .findFirst()
                            .ifPresent(c -> {
                                entity.setLatitude(new BigDecimal(c.StationLatitude()));
                                entity.setLongitude(new BigDecimal(c.StationLongitude()));
                            });
                }
            }
            stationRepo.save(entity);
        } catch (Exception e) {
            log.debug("[CWA_RAINFALL] Station {} already exists or save failed: {}",
                    station.StationId(), e.getMessage());
        }
    }
}
