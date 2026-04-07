package com.rstltd.skypulse.backfill;

import com.rstltd.skypulse.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Backfill", description = "Historical data import (requires ADMIN role)")
@RestController
@RequestMapping("/api/v1/backfill")
public class BackfillController {

    private final BackfillService backfillService;

    public BackfillController(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Operation(summary = "Trigger historical data backfill", description = "Import historical data for the specified source and date range. Backfill must be enabled in configuration.")
    @PostMapping("/{source}")
    public ResponseEntity<ApiResponse<BackfillResult>> triggerBackfill(
            @PathVariable String source,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (!backfillService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Backfill is disabled. Set skypulse.backfill.enabled=true"));
        }

        if (startDate.isAfter(endDate)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("startDate must be before endDate"));
        }

        BackfillResult result = backfillService.executeBackfill(source, startDate, endDate);

        if (result.errors() > 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(result.message()));
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
