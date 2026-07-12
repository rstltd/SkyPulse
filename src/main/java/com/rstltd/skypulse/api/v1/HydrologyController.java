package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.ReservoirView;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.service.HydrologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Hydrology", description = "Water levels and reservoir status from WRA")
@RestController
@RequestMapping("/api/v1/hydrology")
@Validated
public class HydrologyController {

    private final HydrologyService hydrologyService;

    public HydrologyController(HydrologyService hydrologyService) {
        this.hydrologyService = hydrologyService;
    }

    @Operation(summary = "Get latest water levels", description = "Returns observations from the last 2 hours across all water level stations.")
    @GetMapping("/water-level/latest")
    public ResponseEntity<ApiResponse<List<WaterLevelObservation>>> getLatestWaterLevels() {
        var data = hydrologyService.getLatestWaterLevels();
        return ResponseEntity.ok()
                .header("X-Data-Window", "2h")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Get water level by station", description = "Returns water level time series. Max 168 hours (7 days).")
    @GetMapping("/water-level/station/{code}")
    public ApiResponse<List<WaterLevelObservation>> getWaterLevelByStation(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        return ApiResponse.ok(hydrologyService.getWaterLevelByStation(code, hours));
    }

    @Operation(summary = "Get latest reservoir status", description = "Returns current status of all monitored reservoirs, sorted north to south.")
    @GetMapping("/reservoirs")
    public ResponseEntity<ApiResponse<List<ReservoirView>>> getReservoirs() {
        var data = hydrologyService.getLatestReservoirStatus();
        return ResponseEntity.ok()
                .header("X-Data-Window", "25h")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }
}
