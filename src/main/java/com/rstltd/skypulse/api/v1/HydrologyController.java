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
    public ApiResponse<List<WaterLevelObservation>> getWaterLevelByStation(@PathVariable String code) {
        return ApiResponse.ok(hydrologyService.getWaterLevelByStation(code));
    }

    @GetMapping("/reservoirs")
    public ApiResponse<List<ReservoirStatus>> getReservoirs() {
        return ApiResponse.ok(hydrologyService.getLatestReservoirStatus());
    }
}
