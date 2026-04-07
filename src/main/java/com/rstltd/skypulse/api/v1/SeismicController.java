package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.service.SeismicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Seismic", description = "Earthquake events from CWA and USGS")
@RestController
@RequestMapping("/api/v1/seismic")
@Validated
public class SeismicController {

    private final SeismicService seismicService;

    public SeismicController(SeismicService seismicService) {
        this.seismicService = seismicService;
    }

    @Operation(summary = "Get latest earthquake events", description = "Returns deduplicated events from the last 7 days. CWA records preferred over USGS.")
    @GetMapping("/events/latest")
    public ResponseEntity<ApiResponse<List<EarthquakeEvent>>> getLatestEvents() {
        var data = seismicService.getLatestEvents();
        return ResponseEntity.ok()
                .header("X-Data-Window", "7d")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Get earthquake events (paginated)", description = "Returns filtered and paginated earthquake events.")
    @GetMapping("/events")
    public ApiResponse<PagedResponse<EarthquakeEvent>> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) BigDecimal minMag,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                seismicService.getEventsSince(since, minMag, PageRequest.of(page, size))));
    }

    @Operation(summary = "Get nearby earthquake events", description = "Returns events within specified radius of coordinates (last 30 days).")
    @GetMapping("/events/nearby")
    public ApiResponse<List<EarthquakeEvent>> getNearbyEvents(
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lon,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) double radiusKm) {
        return ApiResponse.ok(seismicService.getNearbyEvents(lat, lon, radiusKm));
    }
}
