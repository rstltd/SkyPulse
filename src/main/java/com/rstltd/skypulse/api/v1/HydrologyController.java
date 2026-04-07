package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;
import com.rstltd.skypulse.domain.hydrology.WaterLevelObservation;
import com.rstltd.skypulse.service.HydrologyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hydrology")
public class HydrologyController {

    private final HydrologyService hydrologyService;

    public HydrologyController(HydrologyService hydrologyService) {
        this.hydrologyService = hydrologyService;
    }

    @GetMapping("/water-level/latest")
    public ApiResponse<List<WaterLevelObservation>> getLatestWaterLevels() {
        return ApiResponse.ok(hydrologyService.getLatestWaterLevels());
    }

    @GetMapping("/water-level/station/{code}")
    public ApiResponse<List<WaterLevelObservation>> getWaterLevelByStation(
            @PathVariable String code,
            @RequestParam(defaultValue = "24") int hours) {
        int clampedHours = Math.min(Math.max(hours, 1), 168);
        return ApiResponse.ok(hydrologyService.getWaterLevelByStation(code, clampedHours));
    }

    @GetMapping("/reservoirs")
    public ApiResponse<List<ReservoirStatus>> getReservoirs() {
        return ApiResponse.ok(hydrologyService.getLatestReservoirStatus());
    }
}
