package com.rstltd.skypulse.collector.cwa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rstltd.skypulse.collector.common.CollectorBase;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.collector.cwa.dto.CwaForecastResponse;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.repository.WeatherForecastRepository;
import com.rstltd.skypulse.util.TimeUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class CwaForecastCollector extends CollectorBase<CwaForecastResponse.Location> {

    private final CwaApiClient cwaApiClient;
    private final WeatherForecastRepository forecastRepo;
    private final ObjectMapper objectMapper;

    public CwaForecastCollector(CwaApiClient cwaApiClient,
                                WeatherForecastRepository forecastRepo,
                                ObjectMapper objectMapper) {
        this.cwaApiClient = cwaApiClient;
        this.forecastRepo = forecastRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${skypulse.cwa.schedule.forecast}")
    public CollectorResult collect() {
        return execute();
    }

    @Override
    protected String getSourceName() {
        return "CWA_FORECAST";
    }

    @Override
    protected Mono<List<CwaForecastResponse.Location>> fetch() {
        return cwaApiClient.getDataset("F-D0047-091", CwaForecastResponse.class)
                .map(response -> {
                    if (response.result() != null && response.result().records() != null
                            && response.result().records().Locations() != null
                            && !response.result().records().Locations().isEmpty()
                            && response.result().records().Locations().get(0).Location() != null) {
                        return response.result().records().Locations().get(0).Location();
                    }
                    return Collections.<CwaForecastResponse.Location>emptyList();
                });
    }

    @Override
    protected boolean validate(CwaForecastResponse.Location item) {
        return item.LocationName() != null
                && item.WeatherElement() != null
                && !item.WeatherElement().isEmpty();
    }

    @Override
    protected int persist(List<CwaForecastResponse.Location> data) {
        OffsetDateTime issuedTime = TimeUtils.nowUtc();
        List<WeatherForecast> forecasts = new ArrayList<>();

        for (var location : data) {
            forecasts.addAll(mapToEntities(location, issuedTime));
        }

        if (!forecasts.isEmpty()) {
            forecastRepo.saveAll(forecasts);
            forecastRepo.flush();
        }
        return forecasts.size();
    }

    private List<WeatherForecast> mapToEntities(CwaForecastResponse.Location location,
                                                 OffsetDateTime issuedTime) {
        // Collect time periods from the first weather element (temperature)
        var elements = location.WeatherElement();
        if (elements == null || elements.isEmpty()) return Collections.emptyList();

        // Find key elements by name
        var tempElement = findElement(elements, "平均溫度");
        var wxElement = findElement(elements, "天氣現象");
        var popElement = findElement(elements, "降雨機率");
        var minTElement = findElement(elements, "最低溫度");
        var maxTElement = findElement(elements, "最高溫度");

        // Use temperature time entries as the reference
        var refElement = tempElement != null ? tempElement : elements.get(0);
        if (refElement.Time() == null) return Collections.emptyList();

        List<WeatherForecast> results = new ArrayList<>();
        for (int i = 0; i < refElement.Time().size(); i++) {
            var timeEntry = refElement.Time().get(i);
            WeatherForecast fc = new WeatherForecast();
            fc.setLocationName(location.LocationName());
            fc.setForecastTime(TimeUtils.toUtcOffset(
                    TimeUtils.parseIsoOffset(timeEntry.StartTime())));
            fc.setIssuedTime(issuedTime);
            fc.setSource("CWA");

            // Temperature
            if (tempElement != null && i < tempElement.Time().size()) {
                var val = getFirstValue(tempElement.Time().get(i));
                if (val != null && val.Temperature() != null) {
                    BigDecimal temp = parseSafe(val.Temperature());
                    fc.setMinTemp(temp);
                    fc.setMaxTemp(temp);
                }
            }
            if (minTElement != null && i < minTElement.Time().size()) {
                var val = getFirstValue(minTElement.Time().get(i));
                if (val != null && val.MinTemperature() != null) {
                    fc.setMinTemp(parseSafe(val.MinTemperature()));
                }
            }
            if (maxTElement != null && i < maxTElement.Time().size()) {
                var val = getFirstValue(maxTElement.Time().get(i));
                if (val != null && val.MaxTemperature() != null) {
                    fc.setMaxTemp(parseSafe(val.MaxTemperature()));
                }
            }
            // Weather description
            if (wxElement != null && i < wxElement.Time().size()) {
                var val = getFirstValue(wxElement.Time().get(i));
                if (val != null && val.Weather() != null) {
                    fc.setWeatherDesc(val.Weather());
                }
            }
            // Rain probability
            if (popElement != null && i < popElement.Time().size()) {
                var val = getFirstValue(popElement.Time().get(i));
                if (val != null && val.ProbabilityOfPrecipitation() != null) {
                    try {
                        fc.setRainProb(Integer.parseInt(val.ProbabilityOfPrecipitation()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            try {
                fc.setRawData(objectMapper.writeValueAsString(location));
            } catch (JsonProcessingException ignored) {}

            results.add(fc);
        }
        return results;
    }

    private CwaForecastResponse.WeatherElement findElement(
            List<CwaForecastResponse.WeatherElement> elements, String name) {
        return elements.stream()
                .filter(e -> name.equals(e.ElementName()))
                .findFirst()
                .orElse(null);
    }

    private CwaForecastResponse.ElementValue getFirstValue(CwaForecastResponse.TimeEntry entry) {
        if (entry.ElementValue() != null && !entry.ElementValue().isEmpty()) {
            return entry.ElementValue().get(0);
        }
        return null;
    }

    private BigDecimal parseSafe(String value) {
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
