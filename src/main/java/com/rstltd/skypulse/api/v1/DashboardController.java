package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.DashboardResponse;
import com.rstltd.skypulse.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Consolidated data for GNSS monitoring sites")
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
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) String stationCode) {
        return ApiResponse.ok(dashboardService.getDashboard(siteId, lat, lon, stationCode));
    }
}
