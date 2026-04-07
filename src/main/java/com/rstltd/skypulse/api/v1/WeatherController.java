package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.EffectiveRainfallResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.service.WeatherService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/rainfall/latest")
    public ApiResponse<List<RainfallObservation>> getLatestRainfall() {
        return ApiResponse.ok(weatherService.getLatestRainfall());
    }

    @GetMapping("/rainfall/station/{code}")
    public ApiResponse<List<RainfallObservation>> getRainfallByStation(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        return ApiResponse.ok(weatherService.getRainfallByStation(code, hours));
    }

    @GetMapping("/rainfall/accumulated")
    public ApiResponse<Map<String, Object>> getAccumulatedRainfall(
            @RequestParam String stationCode,
            @RequestParam(defaultValue = "24") int hours) {
        BigDecimal accumulated = weatherService.getAccumulatedRainfall(stationCode, hours);
        return ApiResponse.ok(Map.of(
                "stationCode", stationCode,
                "hours", hours,
                "accumulatedPrecipitation", accumulated
        ));
    }

    @GetMapping("/rainfall/effective")
    public ApiResponse<EffectiveRainfallResponse> getEffectiveRainfall(
            @RequestParam String stationCode,
            @RequestParam(defaultValue = "72") @Min(1) @Max(720) int windowHours,
            @RequestParam(required = false) String endTime) {
        java.time.OffsetDateTime end = null;
        if (endTime != null && !endTime.isBlank()) {
            end = java.time.OffsetDateTime.parse(endTime);
        }
        return ApiResponse.ok(weatherService.getEffectiveRainfall(stationCode, windowHours, end));
    }

    @GetMapping("/observations/latest")
    public ApiResponse<List<WeatherObservation>> getLatestObservations() {
        return ApiResponse.ok(weatherService.getLatestObservations());
    }

    @GetMapping("/forecasts/{location}")
    public ApiResponse<List<WeatherForecast>> getForecasts(@PathVariable String location) {
        return ApiResponse.ok(weatherService.getForecastsByLocation(location));
    }
}
