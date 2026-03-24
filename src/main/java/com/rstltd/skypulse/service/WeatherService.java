package com.rstltd.skypulse.service;

import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.repository.WeatherForecastRepository;
import com.rstltd.skypulse.repository.WeatherObservationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class WeatherService {

    private final RainfallObservationRepository rainfallRepo;
    private final WeatherObservationRepository weatherRepo;
    private final WeatherForecastRepository forecastRepo;

    public WeatherService(RainfallObservationRepository rainfallRepo,
                          WeatherObservationRepository weatherRepo,
                          WeatherForecastRepository forecastRepo) {
        this.rainfallRepo = rainfallRepo;
        this.weatherRepo = weatherRepo;
        this.forecastRepo = forecastRepo;
    }

    public List<RainfallObservation> getLatestRainfall() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return rainfallRepo.findByTimeBetween(now.minusHours(2), now);
    }

    public List<RainfallObservation> getRainfallByStation(String stationCode) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return rainfallRepo.findByStationCodeAndTimeBetween(
                stationCode, now.minusHours(24), now);
    }

    public BigDecimal getAccumulatedRainfall(String stationCode, int hours) {
        OffsetDateTime now = TimeUtils.nowUtc();
        List<RainfallObservation> observations = rainfallRepo.findByStationCodeAndTimeBetween(
                stationCode, now.minusHours(hours), now);
        return observations.stream()
                .map(RainfallObservation::getPrecipitation)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<WeatherObservation> getLatestObservations() {
        OffsetDateTime now = TimeUtils.nowUtc();
        return weatherRepo.findByTimeBetween(now.minusHours(2), now);
    }

    public List<WeatherForecast> getForecastsByLocation(String locationName) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return forecastRepo.findByLocationNameAndForecastTimeAfter(locationName, now);
    }
}
