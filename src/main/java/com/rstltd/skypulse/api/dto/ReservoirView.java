package com.rstltd.skypulse.api.dto;

import com.rstltd.skypulse.domain.hydrology.Reservoir;
import com.rstltd.skypulse.domain.hydrology.ReservoirStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Read model joining a reservoir's latest {@link ReservoirStatus} measurement with its
 * static {@link Reservoir} dimension (name, full level, coordinates). This is what the
 * hydrology / dashboard APIs expose so the reservoir-name and coordinate contract survives
 * the status/dimension table split.
 */
public record ReservoirView(
        OffsetDateTime time,
        String reservoirId,
        String reservoirName,
        BigDecimal waterLevelM,
        BigDecimal fullLevelM,
        BigDecimal effectiveStorageM3,
        BigDecimal designCapacityM3,
        BigDecimal storagePct,
        BigDecimal inflowCms,
        BigDecimal outflowCms,
        BigDecimal catchmentRainMm,
        BigDecimal latitude,
        BigDecimal longitude,
        String basin,
        String county
) {
    /** Assemble a view from a status row and its (possibly null) dimension row. */
    public static ReservoirView of(ReservoirStatus s, Reservoir dim) {
        return new ReservoirView(
                s.getTime(),
                s.getReservoirId(),
                dim != null ? dim.getReservoirName() : null,
                s.getWaterLevelM(),
                dim != null ? dim.getFullLevelM() : null,
                s.getEffectiveStorageM3(),
                dim != null ? dim.getDesignCapacityM3() : null,
                s.getStoragePct(),
                s.getInflowCms(),
                s.getOutflowCms(),
                s.getCatchmentRainMm(),
                dim != null ? dim.getLatitude() : null,
                dim != null ? dim.getLongitude() : null,
                dim != null ? dim.getBasin() : null,
                dim != null ? dim.getCounty() : null);
    }
}
