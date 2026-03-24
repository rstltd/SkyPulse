package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.domain.weather.RainfallObservation;
import com.rstltd.skypulse.domain.weather.WeatherForecast;
import com.rstltd.skypulse.domain.weather.WeatherObservation;
import com.rstltd.skypulse.service.WeatherService;
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
    public ApiResponse<List<RainfallObservation>> getRainfallByStation(@PathVariable String code) {
        return ApiResponse.ok(weatherService.getRainfallByStation(code));
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

    @GetMapping("/observations/latest")
    public ApiResponse<List<WeatherObservation>> getLatestObservations() {
        return ApiResponse.ok(weatherService.getLatestObservations());
    }

    @GetMapping("/forecasts/{location}")
    public ApiResponse<List<WeatherForecast>> getForecasts(@PathVariable String location) {
        return ApiResponse.ok(weatherService.getForecastsByLocation(location));
    }
}
