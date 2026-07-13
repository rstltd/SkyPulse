package com.rstltd.skypulse.api.dto.context;

import java.time.OffsetDateTime;

/** Response-level metadata. {@code partial} is true when any requested block came back null. */
public record ResponseMeta(
        OffsetDateTime generatedAt,
        String contractVersion,
        boolean partial
) {
    public static final String CONTRACT_VERSION = "1.0";
}
