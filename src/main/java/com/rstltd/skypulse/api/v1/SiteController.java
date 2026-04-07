package com.rstltd.skypulse.api.v1;

import com.rstltd.skypulse.api.dto.ApiResponse;
import com.rstltd.skypulse.api.dto.SiteInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sites")
@Tag(name = "Sites", description = "GNSS monitoring site discovery")
public class SiteController {

    private final SiteProperties siteProperties;

    public SiteController(SiteProperties siteProperties) {
        this.siteProperties = siteProperties;
    }

    @GetMapping
    @Operation(summary = "List all configured GNSS monitoring sites")
    public ApiResponse<List<SiteInfo>> getSites() {
        return ApiResponse.ok(siteProperties.getList());
    }

    @Component
    @ConfigurationProperties(prefix = "skypulse.sites")
    public static class SiteProperties {
        private List<SiteInfo> list = List.of();

        public List<SiteInfo> getList() {
            return list;
        }

        public void setList(List<SiteInfo> list) {
            this.list = list;
        }
    }
}
