package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.context.ContextResponse;
import com.rstltd.skypulse.service.ContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Set;

/**
 * Coordinate-query API (Direction B main product): integrated public environmental context for a
 * GNSS site coordinate. Stateless — SkyPulse stores no site coordinates.
 */
@Tag(name = "Context", description = "Coordinate-based integrated environmental context")
@RestController
@RequestMapping("/api/v1/context")
@Validated
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @Operation(summary = "Environmental context for a coordinate",
            description = "Nearest-station rainfall/water level, nearby seismicity, and global GNSS "
                    + "quality for a single WGS84 coordinate. Missing blocks come back null with a "
                    + "matching entry in `warnings`; valid coordinates outside Taiwan return 200 with "
                    + "OUTSIDE_COVERAGE.")
    @GetMapping
    public ResponseEntity<ApiResponse<ContextResponse>> getContext(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double radiusKm,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double rainRadiusKm,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double waterRadiusKm,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("300") Double quakeRadiusKm,
            @RequestParam(required = false) Set<String> include) {
        ContextResponse data = contextService.getContext(
                lat, lon, radiusKm, rainRadiusKm, waterRadiusKm, quakeRadiusKm, include);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)))
                .body(ApiResponse.ok(data));
    }
}
