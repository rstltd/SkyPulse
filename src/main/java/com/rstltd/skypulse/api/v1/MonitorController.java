package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.GnssQualityResponse;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.service.AlertService;
import com.rstltd.skypulse.service.CollectorStatusService;
import com.rstltd.skypulse.service.SeismicService;
import com.rstltd.skypulse.service.SpaceWeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public monitoring endpoint for the Dashboard HTML page.
 * No authentication required — returns read-only summary data.
 */
@Tag(name = "Monitor", description = "Public system monitoring summary")
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final SpaceWeatherService spaceWeatherService;
    private final CollectorStatusService collectorStatusService;
    private final SeismicService seismicService;
    private final AlertService alertService;

    public MonitorController(SpaceWeatherService spaceWeatherService,
                             CollectorStatusService collectorStatusService,
                             SeismicService seismicService,
                             AlertService alertService) {
        this.spaceWeatherService = spaceWeatherService;
        this.collectorStatusService = collectorStatusService;
        this.seismicService = seismicService;
        this.alertService = alertService;
    }

    @Operation(summary = "Get global monitoring summary", description = "Returns GNSS quality, collector statuses, recent earthquakes (max 10), and active alerts (max 10). No authentication required.")
    @GetMapping("/summary")
    public ApiResponse<MonitorSummary> getSummary() {
        return ApiResponse.ok(new MonitorSummary(
                spaceWeatherService.assessGnssQuality(),
                collectorStatusService.getAllStatuses(),
                seismicService.getLatestEvents().stream().limit(10).toList(),
                alertService.getActiveAlerts().stream().limit(10).toList()
        ));
    }

    public record MonitorSummary(
            GnssQualityResponse gnssQuality,
            List<CollectorStatusService.CollectorStatus> collectors,
            List<EarthquakeEvent> recentEarthquakes,
            List<HazardAlert> activeAlerts
    ) {}
}
