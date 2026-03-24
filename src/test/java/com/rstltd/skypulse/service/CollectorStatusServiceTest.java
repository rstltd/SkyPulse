package com.rstltd.skypulse.service;

import com.rstltd.skypulse.collector.common.CollectorResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CollectorStatusServiceTest {

    @Test
    void recordResult_storesLatestStatus() {
        var service = new CollectorStatusService();

        service.recordResult(CollectorResult.success("CWA_RAINFALL", 700, 710, 705, 523));
        service.recordResult(CollectorResult.empty("CWA_ALERT", 100));

        var statuses = service.getAllStatuses();
        assertEquals(2, statuses.size());

        var rainfall = statuses.stream()
                .filter(s -> "CWA_RAINFALL".equals(s.source())).findFirst().orElseThrow();
        assertEquals("SUCCESS", rainfall.status());
        assertEquals(700, rainfall.persistedCount());
    }

    @Test
    void recordResult_overwritesPreviousStatus() {
        var service = new CollectorStatusService();

        service.recordResult(CollectorResult.failure("USGS_EARTHQUAKE", 0, "timeout"));
        service.recordResult(CollectorResult.success("USGS_EARTHQUAKE", 8, 8, 8, 400));

        var statuses = service.getAllStatuses();
        assertEquals(1, statuses.size());
        assertEquals("SUCCESS", statuses.get(0).status());
        assertEquals(8, statuses.get(0).persistedCount());
    }

    @Test
    void getAllStatuses_sortedBySource() {
        var service = new CollectorStatusService();
        service.recordResult(CollectorResult.empty("WRA_WATER_LEVEL", 50));
        service.recordResult(CollectorResult.empty("CWA_RAINFALL", 50));
        service.recordResult(CollectorResult.empty("SWPC_KP_INDEX", 50));

        var statuses = service.getAllStatuses();
        assertEquals("CWA_RAINFALL", statuses.get(0).source());
        assertEquals("SWPC_KP_INDEX", statuses.get(1).source());
        assertEquals("WRA_WATER_LEVEL", statuses.get(2).source());
    }
}
