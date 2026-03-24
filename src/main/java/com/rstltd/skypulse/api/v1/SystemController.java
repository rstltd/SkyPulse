package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.service.CollectorStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    private final CollectorStatusService statusService;

    public SystemController(CollectorStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/collectors")
    public ApiResponse<List<CollectorStatusService.CollectorStatus>> getCollectorStatuses() {
        return ApiResponse.ok(statusService.getAllStatuses());
    }
}
