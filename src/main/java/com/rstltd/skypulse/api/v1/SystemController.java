package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.LogStatsResponse;
import com.rstltd.skypulse.api.dto.PagedResponse;
import com.rstltd.skypulse.api.dto.SystemLogDto;
import com.rstltd.skypulse.service.CollectorStatusService;
import com.rstltd.skypulse.service.SystemLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@Tag(name = "System", description = "System administration (requires ADMIN role)")
@RestController
@RequestMapping("/api/v1/system")
@Validated
public class SystemController {

    private final CollectorStatusService statusService;
    private final SystemLogService systemLogService;

    public SystemController(CollectorStatusService statusService,
                            SystemLogService systemLogService) {
        this.statusService = statusService;
        this.systemLogService = systemLogService;
    }

    @Operation(summary = "Get data collector statuses")
    @GetMapping("/collectors")
    public ApiResponse<List<CollectorStatusService.CollectorStatus>> getCollectorStatuses() {
        return ApiResponse.ok(statusService.getAllStatuses());
    }

    @Operation(summary = "Query system logs (paginated)", description = "Filter logs by category, level, source, keyword, and date range.")
    @GetMapping("/logs")
    public ApiResponse<PagedResponse<SystemLogDto>> getLogs(
            @Parameter(schema = @Schema(allowableValues = {"COLLECTOR", "SYSTEM", "BACKFILL", "ERROR"})) @RequestParam(required = false) String category,
            @Parameter(schema = @Schema(allowableValues = {"INFO", "WARN", "ERROR"})) @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var logs = systemLogService.queryLogs(category, level, source, keyword,
                start, end, PageRequest.of(page, Math.min(size, 100)));

        var dtoPage = logs.map(SystemLogDto::from);
        return ApiResponse.ok(PagedResponse.from(dtoPage));
    }

    @Operation(summary = "Get log statistics")
    @GetMapping("/logs/stats")
    public ApiResponse<LogStatsResponse> getLogStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {

        var stats = systemLogService.getStats(start, end);
        return ApiResponse.ok(LogStatsResponse.from(stats));
    }

    @Operation(summary = "Get distinct log source names")
    @GetMapping("/logs/sources")
    public ApiResponse<List<String>> getLogSources() {
        return ApiResponse.ok(systemLogService.getDistinctSources());
    }
}
