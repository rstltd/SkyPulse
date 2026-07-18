package com.rstltd.skypulse.domain.station;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link StationCapability}. */
public class StationCapabilityId implements Serializable {

    private String stationCode;
    private String capability;

    public StationCapabilityId() {}

    public StationCapabilityId(String stationCode, String capability) {
        this.stationCode = stationCode;
        this.capability = capability;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StationCapabilityId that)) return false;
        return Objects.equals(stationCode, that.stationCode)
                && Objects.equals(capability, that.capability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stationCode, capability);
    }
}
