package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaWeatherResponse;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.repository.WeatherObservationRepository;
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
public class CwaWeatherCollector extends CollectorBase<CwaWeatherResponse.Station> {

    private final CwaApiClient cwaApiClient;
    private final WeatherObservationRepository weatherRepo;
    private final StationRegistry stationRegistry;
    private final ObjectMapper objectMapper;

    public CwaWeatherCollector(CwaApiClient cwaApiClient,
                               WeatherObservationRepository weatherRepo,
                               StationRegistry stationRegistry,
                               ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.weatherRepo = weatherRepo;
        this.stationRegistry = stationRegistry;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.weather}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_WEATHER";
    }

    @Override
    protected Mono<List<CwaWeatherResponse.Station>> fetch() {
        return cwaApiClient.getDataset("O-A0001-001", CwaWeatherResponse.class)
                .map(response -> {
                    if (response.records() != null && response.records().Station() != null) {
                        return response.records().Station();
                    }
                    return Collections.<CwaWeatherResponse.Station>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaWeatherResponse.Station item) {
        if (item.StationId() == null || item.ObsTime() == null
                || item.ObsTime().DateTime() == null || item.WeatherElement() == null) {
            return false;
        }
        // At least temperature should be valid
        return parseSafe(item.WeatherElement().AirTemperature()) != null;
    }

    @Override
    protected int persist(List<CwaWeatherResponse.Station> data) {
        if (data.isEmpty()) return 0;

        OffsetDateTime batchTime = TimeUtils.toUtcOffset(
                TimeUtils.parseIsoOffset(data.get(0).ObsTime().DateTime()));
        Set<String> existingKeys = weatherRepo
                .findByTimeBetween(batchTime.minusMinutes(1), batchTime.plusMinutes(1))
                .stream()
                .map(r -> r.getTime() + "|" + r.getStationCode())
                .collect(Collectors.toSet());

        List<WeatherObservation> newObs = data.stream()
                .map(this::mapToEntity)
                .filter(obs -> !existingKeys.contains(obs.getTime() + "|" + obs.getStationCode()))
                .toList();

        data.forEach(this::ensureStation);

        if (!newObs.isEmpty()) {
            weatherRepo.saveAll(newObs);
            weatherRepo.flush();
        }
        return newObs.size();
    }

    private WeatherObservation mapToEntity(CwaWeatherResponse.Station station) {
        var we = station.WeatherElement();
        WeatherObservation obs = new WeatherObservation();
        obs.setTime(TimeUtils.toUtcOffset(
                TimeUtils.parseIsoOffset(station.ObsTime().DateTime())));
        obs.setStationCode(station.StationId());
        obs.setTemperature(parseSafe(we.AirTemperature()));
        obs.setHumidity(parseSafe(we.RelativeHumidity()));
        obs.setPressure(parseSafe(we.AirPressure()));
        obs.setWindSpeed(parseSafe(we.WindSpeed()));
        obs.setWindDirection(parseSafe(we.WindDirection()));
        if (we.Now() != null) {
            obs.setPrecipitation(parseSafe(we.Now().Precipitation()));
        }
        obs.setSource("CWA");
        try {
            obs.setRawData(objectMapper.writeValueAsString(station));
        } catch (JsonProcessingException e) {
            log.warn("[CWA_WEATHER] Failed to serialize raw data for station {}", station.StationId());
        }
        return obs;
    }

    private BigDecimal parseSafe(String value) {
        if (value == null) return null;
        try {
            double d = Double.parseDouble(value);
            if (d <= -99) return null; // CWA sentinel values
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ensureStation(CwaWeatherResponse.Station station) {
        try {
            var geo = station.GeoInfo();
            BigDecimal lat = null, lon = null, altitude = null;
            String county = null, township = null;
            if (geo != null) {
                county = geo.CountyName();
                township = geo.TownName();
                altitude = parseSafe(geo.StationAltitude());
                if (geo.Coordinates() != null) {
                    var wgs = geo.Coordinates().stream()
                            .filter(c -> "WGS84".equals(c.CoordinateName()))
                            .findFirst().orElse(null);
                    if (wgs != null) {
                        lat = parseSafe(wgs.StationLatitude());
                        lon = parseSafe(wgs.StationLongitude());
                    }
                }
            }
            stationRegistry.register(station.StationId(), station.StationName(), "CWA",
                    lat, lon, altitude, county, township, "WEATHER", "O-A0001-001");
        } catch (Exception e) {
            log.debug("[CWA_WEATHER] Station register failed: {}", e.getMessage());
        }
    }
}
