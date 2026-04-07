package com.rstltd.skypulse.collector.wra;

import java.math.BigDecimal;

public record ReservoirRefData(
        String name,
        BigDecimal fullLevel,
        BigDecimal capacity
) {}
