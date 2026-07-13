package com.rstltd.skypulse.api.dto.context;

import java.util.List;

/** Batch results in request order; each item echoes the site {@code id} (may be null). */
public record BatchContextResponse(
        List<Result> results
) {
    public record Result(String id, ContextResponse context) {}
}
