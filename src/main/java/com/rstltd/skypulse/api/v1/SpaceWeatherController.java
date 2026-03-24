package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import com.rstltd.skypulse.service.SpaceWeatherService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/spaceweather")
@Validated
public class SpaceWeatherController {

    private final SpaceWeatherService spaceWeatherService;

    public SpaceWeatherController(SpaceWeatherService spaceWeatherService) {
        this.spaceWeatherService = spaceWeatherService;
    }

    @GetMapping("/kp/current")
    public ApiResponse<KpIndexRecord> getCurrentKp() {
        return spaceWeatherService.getCurrentKp()
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("No Kp data available"));
    }

    @GetMapping("/kp/history")
    public ApiResponse<PagedResponse<KpIndexRecord>> getKpHistory(
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                spaceWeatherService.getKpHistoryPaged(hours, PageRequest.of(page, size))));
    }

    @GetMapping("/dst/current")
    public ApiResponse<DstIndexRecord> getCurrentDst() {
        return spaceWeatherService.getCurrentDst()
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("No Dst data available"));
    }

    @GetMapping("/dst/history")
    public ApiResponse<PagedResponse<DstIndexRecord>> getDstHistory(
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                spaceWeatherService.getDstHistoryPaged(hours, PageRequest.of(page, size))));
    }

    @GetMapping("/solar-wind/current")
    public ApiResponse<SolarWindRecord> getCurrentSolarWind() {
        return spaceWeatherService.getCurrentSolarWind()
                .map(ApiResponse::ok)
                .orElse(ApiResponse.error("No solar wind data available"));
    }

    @GetMapping("/alerts")
    public ApiResponse<List<SpaceWeatherAlert>> getAlerts() {
        return ApiResponse.ok(spaceWeatherService.getRecentAlerts());
    }

    @GetMapping("/gnss-quality")
    public ApiResponse<GnssQualityResponse> getGnssQuality() {
        return ApiResponse.ok(spaceWeatherService.assessGnssQuality());
    }
}
