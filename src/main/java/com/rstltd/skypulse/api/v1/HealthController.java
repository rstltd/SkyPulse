package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Tag(name = "Health", description = "Service health check")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "Check service health status")
    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now(ZoneOffset.UTC),
                "service", "SkyPulse"
        ));
    }
}
