package com.rstltd.skypulse.domain.hydrology;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ReservoirStatusId implements Serializable {
    private OffsetDateTime time;
    private String reservoirId;
}
