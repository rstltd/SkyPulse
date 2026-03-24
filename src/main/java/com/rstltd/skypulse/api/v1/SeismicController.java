package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.seismic.EarthquakeEvent;
import com.rstltd.skypulse.service.SeismicService;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seismic")
public class SeismicController {

    private final SeismicService seismicService;

    public SeismicController(SeismicService seismicService) {
        this.seismicService = seismicService;
    }

    @GetMapping("/events/latest")
    public ApiResponse<List<EarthquakeEvent>> getLatestEvents() {
        return ApiResponse.ok(seismicService.getLatestEvents());
    }

    @GetMapping("/events")
    public ApiResponse<PagedResponse<EarthquakeEvent>> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(required = false) BigDecimal minMag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PagedResponse.from(
                seismicService.getEventsSince(since, minMag, PageRequest.of(page, size))));
    }

    @GetMapping("/events/nearby")
    public ApiResponse<List<EarthquakeEvent>> getNearbyEvents(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "100") double radiusKm) {
        return ApiResponse.ok(seismicService.getNearbyEvents(lat, lon, radiusKm));
    }
}
