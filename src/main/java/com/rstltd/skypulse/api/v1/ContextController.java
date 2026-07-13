package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.context.BatchContextRequest;
import com.rstltd.skypulse.api.dto.context.BatchContextResponse;
import com.rstltd.skypulse.api.dto.context.ContextResponse;
import com.rstltd.skypulse.api.dto.context.CoverageResponse;
import com.rstltd.skypulse.service.ContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
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

    @Value("${skypulse.context.max-batch-size}")
    private int maxBatchSize;

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

    @Operation(summary = "Environmental context for many coordinates",
            description = "Batch form for a GNSS deployment querying many sites at once. Each site's "
                    + "id (if provided) is echoed back. Rejects batches larger than the configured "
                    + "maximum with BATCH_TOO_LARGE.")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchContextResponse>> batch(@Valid @RequestBody BatchContextRequest req) {
        if (req.sites().size() > maxBatchSize) {
            return ResponseEntity.badRequest().body(ApiResponse.error("BATCH_TOO_LARGE",
                    "Batch size " + req.sites().size() + " exceeds the maximum of " + maxBatchSize));
        }
        List<BatchContextResponse.Result> results = req.sites().stream()
                .map(s -> new BatchContextResponse.Result(s.id(), contextService.getContext(
                        s.lat(), s.lon(), s.radiusKm(), null, null, s.quakeRadiusKm(), req.include())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new BatchContextResponse(results)));
    }

    @Operation(summary = "Coverage for a coordinate",
            description = "Which station each coordinate-dependent domain would use, with distance, "
                    + "and no indicator computation. For transparency / debugging.")
    @GetMapping("/coverage")
    public ResponseEntity<ApiResponse<CoverageResponse>> coverage(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double radiusKm,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double rainRadiusKm,
            @RequestParam(required = false) @DecimalMin("0.1") @DecimalMax("100") Double waterRadiusKm) {
        CoverageResponse data = contextService.getCoverage(lat, lon, radiusKm, rainRadiusKm, waterRadiusKm);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
