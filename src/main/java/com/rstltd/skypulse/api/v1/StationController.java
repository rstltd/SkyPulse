package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.domain.station.Station;
import com.rstltd.skypulse.repository.StationRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationRepository stationRepository;

    public StationController(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @GetMapping
    public ApiResponse<List<Station>> getStations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String source) {
        List<Station> stations;
        if (type != null && !type.isBlank()) {
            stations = stationRepository.findByStationType(type);
        } else if (source != null && !source.isBlank()) {
            stations = stationRepository.findBySource(source);
        } else {
            stations = stationRepository.findAll();
        }
        return ApiResponse.ok(stations);
    }
}
