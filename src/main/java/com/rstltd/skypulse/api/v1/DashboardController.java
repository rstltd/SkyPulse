package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.DashboardResponse;
import com.rstltd.skypulse.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Per-site consolidated dashboard for GNSS monitoring sites.
 * Returns geo-filtered data (earthquakes within 100km, station-specific rainfall, nearby water levels).
 * Reserved for future per-site monitoring view (not yet connected to frontend).
 * For the global system summary used by DashboardView.vue, see {@link MonitorController}.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Consolidated data for GNSS monitoring sites")
@Validated
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/site/{siteId}")
    @Operation(summary = "Get consolidated dashboard data for a GNSS site",
            description = "Returns rainfall summary, GNSS quality, nearby earthquakes, " +
                    "active alerts, water levels, and reservoir status in a single response")
    public ApiResponse<DashboardResponse> getSiteDashboard(
            @PathVariable String siteId,
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lon,
            @RequestParam(required = false) String stationCode) {
        return ApiResponse.ok(dashboardService.getDashboard(siteId, lat, lon, stationCode));
    }
}
