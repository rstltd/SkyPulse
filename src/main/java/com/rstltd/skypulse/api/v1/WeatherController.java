package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.AccumulatedRainfallResponse;
import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.EffectiveRainfallResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Weather", description = "Meteorological observations, rainfall, and forecasts")
@RestController
@RequestMapping("/api/v1/weather")
@Validated
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Operation(summary = "Get latest rainfall observations", description = "Returns observations from the last 2 hours across all stations.")
    @GetMapping("/rainfall/latest")
    public ResponseEntity<ApiResponse<List<RainfallObservation>>> getLatestRainfall() {
        var data = weatherService.getLatestRainfall();
        return ResponseEntity.ok()
                .header("X-Data-Window", "2h")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Get rainfall by station", description = "Returns rainfall time series for a specific station.")
    @GetMapping("/rainfall/station/{code}")
    public ApiResponse<List<RainfallObservation>> getRainfallByStation(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        return ApiResponse.ok(weatherService.getRainfallByStation(code, hours));
    }

    @Operation(summary = "Get accumulated rainfall", description = "Returns total accumulated precipitation for a station over the specified hours.")
    @GetMapping("/rainfall/accumulated")
    public ApiResponse<AccumulatedRainfallResponse> getAccumulatedRainfall(
            @RequestParam String stationCode,
            @RequestParam(defaultValue = "24") @Min(1) @Max(720) int hours) {
        BigDecimal accumulated = weatherService.getAccumulatedRainfall(stationCode, hours);
        return ApiResponse.ok(new AccumulatedRainfallResponse(stationCode, hours, accumulated));
    }

    @Operation(summary = "Get effective rainfall (ETR1/ETR2)", description = "SWCB-standard effective rainfall with decay weighting (T\u00bd=12h) and multi-event detection.")
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

    @Operation(summary = "Get latest weather observations", description = "Returns weather observations from the last 2 hours (temperature, humidity, pressure, wind).")
    @GetMapping("/observations/latest")
    public ResponseEntity<ApiResponse<List<WeatherObservation>>> getLatestObservations() {
        var data = weatherService.getLatestObservations();
        return ResponseEntity.ok()
                .header("X-Data-Window", "2h")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Get weather forecasts by location", description = "Returns CWA forecasts for a county-level location name.")
    @GetMapping("/forecasts/{location}")
    public ApiResponse<List<WeatherForecast>> getForecasts(@PathVariable String location) {
        return ApiResponse.ok(weatherService.getForecastsByLocation(location));
    }
}
