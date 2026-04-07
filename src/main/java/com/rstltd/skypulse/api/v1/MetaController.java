package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.*;
import com.rstltd.skypulse.collector.common.CollectorResult;
import com.rstltd.skypulse.domain.log.LogCategory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
@Tag(name = "Meta", description = "API metadata and enum values")
public class MetaController {

    @GetMapping("/enums")
    @Operation(summary = "List all enum values used in the API")
    public ApiResponse<Map<String, Object>> getEnums() {
        return ApiResponse.ok(Map.of(
                "dataSources", DataSource.values(),
                "stationTypes", StationType.values(),
                "logLevels", LogLevel.values(),
                "logCategories", LogCategory.values(),
                "gnssQualityLevels", GnssQualityLevel.values(),
                "collectorStatuses", CollectorResult.Status.values()
        ));
    }
}
