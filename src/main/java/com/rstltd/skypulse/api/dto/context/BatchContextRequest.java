package com.rstltd.skypulse.api.dto.context;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Batch coordinate-context request: a GNSS deployment queries many sites at once. Each site may
 * carry an optional {@code id} that is echoed back for correlation. {@code include} applies to all.
 */
public record BatchContextRequest(
        @NotEmpty @Valid List<Site> sites,
        Set<String> include
) {
    public record Site(
            String id,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lon,
            @DecimalMin("0.1") @DecimalMax("100") Double radiusKm,
            @DecimalMin("0.1") @DecimalMax("300") Double quakeRadiusKm
    ) {}
}
