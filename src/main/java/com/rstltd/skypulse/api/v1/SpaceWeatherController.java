package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.spaceweather.DstIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.KpIndexRecord;
import com.rstltd.skypulse.domain.spaceweather.SolarWindRecord;
import com.rstltd.skypulse.domain.spaceweather.SpaceWeatherAlert;
import com.rstltd.skypulse.service.SpaceWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Space Weather", description = "Geomagnetic indices, solar wind, and GNSS quality assessment")
@RestController
@RequestMapping("/api/v1/spaceweather")
@Validated
public class SpaceWeatherController {

    private final SpaceWeatherService spaceWeatherService;

    public SpaceWeatherController(SpaceWeatherService spaceWeatherService) {
        this.spaceWeatherService = spaceWeatherService;
    }

    @Operation(summary = "Get current Kp index", description = "Returns the most recent Kp index record.")
    @GetMapping("/kp/current")
    public ResponseEntity<ApiResponse<KpIndexRecord>> getCurrentKp() {
        return spaceWeatherService.getCurrentKp()
                .map(kp -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "1")
                        .body(ApiResponse.ok(kp)))
                .orElseGet(() -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "0")
                        .body(ApiResponse.error("No Kp data available")));
    }

    @Operation(summary = "Get Kp index history (paginated)")
    @GetMapping("/kp/history")
    public ApiResponse<PagedResponse<KpIndexRecord>> getKpHistory(
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                spaceWeatherService.getKpHistoryPaged(hours, PageRequest.of(page, size))));
    }

    @Operation(summary = "Get current Dst index", description = "Returns the most recent Dst index record.")
    @GetMapping("/dst/current")
    public ResponseEntity<ApiResponse<DstIndexRecord>> getCurrentDst() {
        return spaceWeatherService.getCurrentDst()
                .map(dst -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "1")
                        .body(ApiResponse.ok(dst)))
                .orElseGet(() -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "0")
                        .body(ApiResponse.error("No Dst data available")));
    }

    @Operation(summary = "Get Dst index history (paginated)")
    @GetMapping("/dst/history")
    public ApiResponse<PagedResponse<DstIndexRecord>> getDstHistory(
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                spaceWeatherService.getDstHistoryPaged(hours, PageRequest.of(page, size))));
    }

    @Operation(summary = "Get current solar wind data", description = "Returns the most recent solar wind record (speed, density, Bz, Bt).")
    @GetMapping("/solar-wind/current")
    public ResponseEntity<ApiResponse<SolarWindRecord>> getCurrentSolarWind() {
        return spaceWeatherService.getCurrentSolarWind()
                .map(sw -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "1")
                        .body(ApiResponse.ok(sw)))
                .orElseGet(() -> ResponseEntity.ok()
                        .header("X-Data-Window", "latest")
                        .header("X-Data-Count", "0")
                        .body(ApiResponse.error("No solar wind data available")));
    }

    @Operation(summary = "Get space weather alerts", description = "Returns alerts from the last 3 days.")
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<SpaceWeatherAlert>>> getAlerts() {
        var data = spaceWeatherService.getRecentAlerts();
        return ResponseEntity.ok()
                .header("X-Data-Window", "3d")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Assess current GNSS quality", description = "Combines Kp, Dst, and G-scale to classify quality as NORMAL/CAUTION/DEGRADED/SEVERE.")
    @GetMapping("/gnss-quality")
    public ApiResponse<GnssQualityResponse> getGnssQuality() {
        return ApiResponse.ok(spaceWeatherService.assessGnssQuality());
    }

    @Operation(summary = "Get GNSS quality assessment history", description = "Returns quality assessments for each Kp record in the time window. Max 168 hours.")
    @GetMapping("/gnss-quality/history")
    public ApiResponse<List<GnssQualityResponse>> getGnssQualityHistory(
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        return ApiResponse.ok(spaceWeatherService.getGnssQualityHistory(hours));
    }
}
