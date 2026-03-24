package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.service.AlertService;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/active")
    public ApiResponse<List<HazardAlert>> getActiveAlerts() {
        return ApiResponse.ok(alertService.getActiveAlerts());
    }

    @GetMapping
    public ApiResponse<PagedResponse<HazardAlert>> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PagedResponse.from(
                alertService.getAlertsPaged(type, since, PageRequest.of(page, size))));
    }
}
