package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.domain.alert.HazardAlert;
import com.rstltd.skypulse.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "Alerts", description = "Hazard alerts from CWA")
@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "Get active alerts", description = "Returns alerts from the last 24 hours.")
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<HazardAlert>>> getActiveAlerts() {
        var data = alertService.getActiveAlerts();
        return ResponseEntity.ok()
                .header("X-Data-Window", "24h")
                .header("X-Data-Count", String.valueOf(data.size()))
                .body(ApiResponse.ok(data));
    }

    @Operation(summary = "Get alert history (paginated)", description = "Returns filtered and paginated alert history.")
    @GetMapping
    public ApiResponse<PagedResponse<HazardAlert>> getAlerts(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime since,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        return ApiResponse.ok(PagedResponse.from(
                alertService.getAlertsPaged(type, since, PageRequest.of(page, size))));
    }
}
