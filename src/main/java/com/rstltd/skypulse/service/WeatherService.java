package com.rstltd.skypulse.service;

import com.rstltd.skypulse.api.dto.EffectiveRainfallResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.repository.RainfallObservationRepository;
import com.rstltd.skypulse.repository.WeatherForecastRepository;
import com.rstltd.skypulse.repository.WeatherObservationRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    public List<RainfallObservation> getRainfallByStation(String stationCode, int hours) {
        OffsetDateTime now = TimeUtils.nowUtc();
        return rainfallRepo.findByStationCodeAndTimeBetween(
                stationCode, now.minusHours(hours), now);
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

    /**
     * Calculate effective accumulated rainfall using SWCB standard.
     * Formula: R_eff = Σ(precip_1hr_i × 0.5^(t_i / T½))
     * Default: T½ = 12 hours, window = 72 hours
     *
     * @param stationCode station to query
     * @param windowHours lookback window in hours (default 72)
     * @param endTime     end time for calculation (default: now)
     */
    public EffectiveRainfallResponse getEffectiveRainfall(String stationCode,
                                                           int windowHours,
                                                           OffsetDateTime endTime) {
        final BigDecimal HALF_LIFE = new BigDecimal("12");
        final double EVENT_SPLIT_THRESHOLD = 4.0; // 6h < 4mm = event boundary

        OffsetDateTime end = endTime != null ? endTime : TimeUtils.nowUtc();
        // Fetch extra 24h buffer to capture event starts before the window
        int fetchHours = windowHours + 24;
        List<RainfallObservation> allObs = new ArrayList<>(
                rainfallRepo.findByStationCodeAndTimeBetween(
                        stationCode, end.minusHours(fetchHours), end));
        allObs.sort(Comparator.comparing(RainfallObservation::getTime));

        // --- Determine event membership using precip_6hr (CWA official 6h accumulation) ---
        // inEvent[i] = true if the 6h rainfall at point i >= 4mm
        boolean[] inEvent = new boolean[allObs.size()];
        for (int i = 0; i < allObs.size(); i++) {
            BigDecimal p6h = allObs.get(i).getPrecip6hr();
            if (p6h != null) {
                inEvent[i] = p6h.doubleValue() >= EVENT_SPLIT_THRESHOLD;
            } else {
                // Fallback: compute from precip_1hr if precip_6hr unavailable
                inEvent[i] = compute6hSum(allObs, i) >= EVENT_SPLIT_THRESHOLD;
            }
        }

        // --- Compute ETR1 for all events (multi-event segmentation) ---
        BigDecimal[] etr1Values = new BigDecimal[allObs.size()];
        BigDecimal etr1Running = BigDecimal.ZERO;
        boolean wasInEvent = false;
        OffsetDateTime latestEventStart = null;

        for (int i = 0; i < allObs.size(); i++) {
            BigDecimal precip = allObs.get(i).getPrecip1hr();
            BigDecimal p = (precip != null && precip.compareTo(BigDecimal.ZERO) > 0) ? precip : BigDecimal.ZERO;

            if (inEvent[i]) {
                if (!wasInEvent) {
                    // New event starts — reset accumulator
                    etr1Running = BigDecimal.ZERO;
                    latestEventStart = allObs.get(i).getTime();
                }
                etr1Running = etr1Running.add(p);
                etr1Values[i] = etr1Running;
                wasInEvent = true;
            } else {
                // Gap between events — ETR1 = 0
                etr1Values[i] = BigDecimal.ZERO;
                wasInEvent = false;
            }
        }

        // --- Build time series within the requested window ---
        OffsetDateTime windowStart = end.minusHours(windowHours);
        List<EffectiveRainfallResponse.TimeSeriesPoint> timeSeries = new ArrayList<>();

        for (int i = 0; i < allObs.size(); i++) {
            RainfallObservation current = allObs.get(i);
            if (current.getTime().isBefore(windowStart)) continue;

            OffsetDateTime currentTime = current.getTime();

            // ETR2: R_eff with decay (uses all data up to this point)
            BigDecimal rEff = BigDecimal.ZERO;
            for (int j = 0; j <= i; j++) {
                BigDecimal precip = allObs.get(j).getPrecip1hr();
                if (precip == null || precip.compareTo(BigDecimal.ZERO) <= 0) continue;
                double hoursAgo = Duration.between(allObs.get(j).getTime(), currentTime).toMinutes() / 60.0;
                double weight = Math.pow(0.5, hoursAgo / HALF_LIFE.doubleValue());
                rEff = rEff.add(precip.multiply(BigDecimal.valueOf(weight), MathContext.DECIMAL64));
            }

            BigDecimal intensity = current.getPrecip1hr() != null ? current.getPrecip1hr() : BigDecimal.ZERO;
            timeSeries.add(new EffectiveRainfallResponse.TimeSeriesPoint(
                    currentTime,
                    intensity.setScale(2, RoundingMode.HALF_UP),
                    rEff.setScale(2, RoundingMode.HALF_UP),
                    etr1Values[i].setScale(2, RoundingMode.HALF_UP)));
        }

        // Current values
        BigDecimal currentReff = BigDecimal.ZERO;
        BigDecimal currentIntensity = BigDecimal.ZERO;
        BigDecimal currentEtr1 = BigDecimal.ZERO;
        if (!timeSeries.isEmpty()) {
            var latest = timeSeries.get(timeSeries.size() - 1);
            currentReff = latest.effectiveAccumulated();
            currentIntensity = latest.intensity();
            currentEtr1 = latest.eventAccumulated();
        }

        // Latest event duration
        BigDecimal eventDuration = BigDecimal.ZERO;
        if (latestEventStart != null) {
            eventDuration = BigDecimal.valueOf(
                    Duration.between(latestEventStart, end).toMinutes() / 60.0)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return new EffectiveRainfallResponse(
                stationCode, end, currentReff, currentIntensity,
                HALF_LIFE, windowHours,
                currentEtr1, latestEventStart, eventDuration,
                timeSeries);
    }

    /**
     * Compute the sum of precip_1hr in the 6 hours ending at sorted[index].
     */
    private double compute6hSum(List<RainfallObservation> sorted, int index) {
        OffsetDateTime endTime = sorted.get(index).getTime();
        OffsetDateTime startTime = endTime.minusHours(6);
        double sum = 0;
        for (int j = index; j >= 0; j--) {
            OffsetDateTime t = sorted.get(j).getTime();
            if (t.isBefore(startTime)) break;
            BigDecimal p = sorted.get(j).getPrecip1hr();
            if (p != null) sum += p.doubleValue();
        }
        return sum;
    }
}
