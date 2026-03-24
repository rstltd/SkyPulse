package com.rstltd.skypulse.backfill;

import com.rstltd.skypulse.api.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/backfill")
public class BackfillController {

    private final BackfillService backfillService;

    public BackfillController(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

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
