package com.rstltd.skypulse.domain.seismic;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class EarthquakeStationIntensityId implements Serializable {
    private String eventId;
    private String stationCode;
}
