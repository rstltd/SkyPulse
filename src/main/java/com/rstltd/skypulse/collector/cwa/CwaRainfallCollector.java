package com.rstltd.skypulse.collector.cwa;

import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaRainfallResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.service.StationRegistry;
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
    private final StationRegistry stationRegistry;

    public CwaRainfallCollector(CwaApiClient cwaApiClient,
                                RainfallObservationRepository rainfallRepo,
                                StationRegistry stationRegistry) {
        this.cwaApiClient = cwaApiClient;
        this.rainfallRepo = rainfallRepo;
        this.stationRegistry = stationRegistry;
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
        var re = station.RainfallElement();
        obs.setRain10minMm(parsePrecip(re.Past10Min()));
        obs.setDailyAccumMm(parsePrecip(re.Now()));
        obs.setTrailing1hrMm(parsePrecip(re.Past1hr()));
        obs.setTrailing3hrMm(parsePrecip(re.Past3hr()));
        obs.setTrailing6hrMm(parsePrecip(re.Past6Hr()));
        obs.setTrailing12hrMm(parsePrecip(re.Past12hr()));
        obs.setTrailing24hrMm(parsePrecip(re.Past24hr()));
        obs.setSource("CWA");
        return obs;
    }

    private BigDecimal parsePrecip(CwaRainfallResponse.PrecipValue pv) {
        if (pv == null || pv.Precipitation() == null) return null;
        try {
            BigDecimal val = new BigDecimal(pv.Precipitation());
            return val.compareTo(BigDecimal.ZERO) >= 0 ? val : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ensureStation(CwaRainfallResponse.Station station) {
        try {
            var geo = station.GeoInfo();
            BigDecimal lat = null, lon = null, altitude = null;
            String county = null, township = null;
            if (geo != null) {
                county = geo.CountyName();
                township = geo.TownName();
                if (geo.StationAltitude() != null) altitude = new BigDecimal(geo.StationAltitude());
                if (geo.Coordinates() != null) {
                    var wgs = geo.Coordinates().stream()
                            .filter(c -> "WGS84".equals(c.CoordinateName()))
                            .findFirst().orElse(null);
                    if (wgs != null) {
                        lat = new BigDecimal(wgs.StationLatitude());
                        lon = new BigDecimal(wgs.StationLongitude());
                    }
                }
            }
            stationRegistry.register(station.StationId(), station.StationName(), "CWA",
                    lat, lon, altitude, county, township, "RAINFALL", "O-A0002-001");
        } catch (Exception e) {
            log.debug("[CWA_RAINFALL] Station register failed for {}: {}",
                    station.StationId(), e.getMessage());
        }
    }
}
